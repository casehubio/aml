package io.casehub.aml.domain;

public record RejectionContext(
        String actionType,
        String workerId,
        String rejectedBy,
        String resolution) {
}
