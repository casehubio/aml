package io.casehub.aml.api.model;

import java.util.Map;

public record WorkerTaskResponse(
    String taskId,
    String capabilityTag,
    String caseId,
    String assigneeId,
    String dispatchedAt,
    Map<String, Object> commandParams,
    Map<String, Object> investigationSummary
) {}
