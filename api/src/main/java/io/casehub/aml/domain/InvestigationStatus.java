package io.casehub.aml.domain;

import io.casehub.api.model.CaseStatus;

public enum InvestigationStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED,
    SUSPENDED;

    public static InvestigationStatus fromCaseStatus(CaseStatus status) {
        return switch (status) {
            case STARTING, RUNNING, WAITING -> IN_PROGRESS;
            case COMPLETED -> COMPLETED;
            case FAULTED, COMPENSATING, COMPENSATED, COMPENSATION_FAULTED -> FAILED;
            case CANCELLED -> CANCELLED;
            case SUSPENDED -> SUSPENDED;
        };
    }

    public String toWireFormat() {
        return name().toLowerCase().replace('_', '-');
    }

    public static InvestigationStatus fromWireFormat(String value) {
        return valueOf(value.toUpperCase().replace('-', '_'));
    }
}
