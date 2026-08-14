package io.casehub.aml.supervisor;

public class InvalidSupervisorResponseException extends RuntimeException {

    public InvalidSupervisorResponseException(String message) {
        super(message);
    }
}
