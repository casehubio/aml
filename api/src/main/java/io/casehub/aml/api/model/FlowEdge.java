package io.casehub.aml.api.model;

/**
 * Directed edge in the investigation flow graph. {@code from → to} means
 * "worker at index {@code from} completed before worker at index {@code to} was scheduled."
 *
 * @param from source node index (into {@link InvestigationFlowResponse#nodes()})
 * @param to target node index
 */
public record FlowEdge(
    int from,
    int to
) {}
