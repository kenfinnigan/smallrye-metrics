/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.smallrye.metrics.exporters;

import io.smallrye.metrics.MetricRegistries;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by bob on 1/22/18.
 */
public class JsonMetadataExporter implements Exporter {

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public StringBuffer exportOneScope(MetricRegistry.Type scope) {
        MetricRegistry registry = MetricRegistries.get(scope);
        if (registry == null) {
            return null;
        }

        JsonObject obj = registryJSON(registry);
        return stringify(obj);
    }

    @Override
    public StringBuffer exportAllScopes() {
        JsonObject obj = rootJSON();
        return stringify(obj);
    }

    @Override
    public StringBuffer exportOneMetric(MetricRegistry.Type scope, MetricID metricID) {
        throw new UnsupportedOperationException("Exporting metadata of one metricID is currently not implemented because it is not possible to perform such export according to specification.");
    }

    @Override
    public StringBuffer exportMetricsByName(MetricRegistry.Type scope, String name) {
        MetricRegistry registry = MetricRegistries.get(scope);
        if (registry == null) {
            return null;
        }

        Metadata metadata = registry.getMetadata().get(name);

        if (metadata == null) {
            return null;
        }

        JsonObjectBuilder builder = Json.createObjectBuilder();
        metricJSON(builder, name, metadata, getKnownTagsByMetricName(registry, name));
        return stringify(builder.build());

    }

    private static final Map<String, ?> JSON_CONFIG = new HashMap<String, Object>() {{
        put(JsonGenerator.PRETTY_PRINTING, true);
    }};

    StringBuffer stringify(JsonObject obj) {
        StringWriter out = new StringWriter();
        try (JsonWriter writer = Json.createWriterFactory(JSON_CONFIG).createWriter(out)) {
            writer.writeObject(obj);
        }
        return out.getBuffer();
    }


    private JsonObject rootJSON() {
        JsonObjectBuilder root = Json.createObjectBuilder();

        root.add("base", registryJSON(MetricRegistries.get(MetricRegistry.Type.BASE)));
        root.add("vendor", registryJSON(MetricRegistries.get(MetricRegistry.Type.VENDOR)));
        root.add("application", registryJSON(MetricRegistries.get(MetricRegistry.Type.APPLICATION)));

        return root.build();
    }

    private JsonObject registryJSON(MetricRegistry registry) {
        JsonObjectBuilder registryJSON = Json.createObjectBuilder();
        Map<String, Metadata> metrics = registry.getMetadata();

        metrics.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(e -> metricJSON(registryJSON, e.getKey(), e.getValue(), getKnownTagsByMetricName(registry, e.getKey())));
        return registryJSON.build();
    }

    private void metricJSON(JsonObjectBuilder registryJSON, String name, Metadata metric, List<List<String>> tagSets) {
        registryJSON.add(name, metricJSON(metric, tagSets));
    }

    private JsonObject metricJSON(Metadata metadata, List<List<String>> tagSets) {
        JsonObjectBuilder obj = Json.createObjectBuilder();

        if (metadata.getUnit().isPresent()) {
            obj.add("unit", metadata.getUnit().get());
        }
        if (metadata.getType() != null) {
            obj.add("type", metadata.getType());
        }
        if (metadata.getDescription().isPresent()) {
            obj.add("description", metadata.getDescription().get());
        }
        if (metadata.getDisplayName() != null) {
            obj.add("displayName", metadata.getDisplayName());
        }

        // append known sets of tags
        JsonArrayBuilder tagsArray = Json.createArrayBuilder();
        tagSets.forEach(tagSet -> {
            JsonArrayBuilder innerArrayBuilder = Json.createArrayBuilder();
            tagSet.forEach(innerArrayBuilder::add);
            tagsArray.add(innerArrayBuilder);
        });
        obj.add("tags", tagsArray);

        return obj.build();
    }

    /**
     * Find all currently existing metrics under this name in this registry and
     * for each of them, convert its tags to a list of strings.
     * Return a list of all these lists (so one item in the outer list will correspond to a metric,
     * one item in each of the inner lists will correspond to a tag pertaining to that metric)
     */
    private List<List<String>> getKnownTagsByMetricName(MetricRegistry registry, String name) {
        return registry.getMetricIDs()
                .stream()
                .filter(id -> id.getName().equals(name))
                .map(id -> id.getTagsAsList()
                        .stream()
                        .map(tag -> tag.getTagName() + "=" + tag.getTagValue())
                        .collect(Collectors.toList())
                )
                .collect(Collectors.toList());
    }
}
