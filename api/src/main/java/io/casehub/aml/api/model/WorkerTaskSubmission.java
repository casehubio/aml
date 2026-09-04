package io.casehub.aml.api.model;

import java.util.Map;

public record WorkerTaskSubmission(
    String type,
    Map<String, Object> result,
    String declineReason,
    String declineDetail
) {}
