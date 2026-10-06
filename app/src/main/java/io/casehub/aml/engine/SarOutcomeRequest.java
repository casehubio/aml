package io.casehub.aml.engine;

import io.casehub.aml.domain.SarVerdict;

/**
 * REST input DTO for the SAR outcome endpoint.
 *
 * <p>Accepts the raw fields without compact-constructor validation, so that a
 * deserialization error from an out-of-range {@code investigationAccuracyScore}
 * is caught as an {@link IllegalArgumentException} inside the resource method
 * (where {@code IllegalArgumentExceptionMapper} can produce a structured 400)
 * rather than as an {@code IOException} from the message body reader (which JAX-RS
 * converts to a bare 400 before exception mappers run).
 */
record SarOutcomeRequest(SarVerdict verdict, String reason, double investigationAccuracyScore) {}
