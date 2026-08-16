package io.casehub.aml.provenance;

import java.util.Map;

public record ProvDocument(
    Map<String, String> prefix,
    Map<String, Map<String, Object>> entity,
    Map<String, Map<String, Object>> activity,
    Map<String, Map<String, Object>> agent,
    Map<String, Map<String, Object>> wasGeneratedBy,
    Map<String, Map<String, Object>> wasAssociatedWith,
    Map<String, Map<String, Object>> wasAttributedTo,
    Map<String, Map<String, Object>> wasDerivedFrom
) {}
