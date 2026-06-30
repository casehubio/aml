package io.casehub.aml.api.model;

import java.util.List;

/**
 * Investigation flow graph for visualization. Reconstructs the directed acyclic graph
 * of specialist workers dispatched during an AML investigation, showing:
 * <ul>
 *   <li>Execution order (nodes in temporal sequence)</li>
 *   <li>Parallel groups (workers scheduled simultaneously)</li>
 *   <li>Trust scores at routing time</li>
 *   <li>Worker status (scheduled/completed/failed)</li>
 * </ul>
 *
 * <p>Edge direction: {@code from → to} means "from completed before to was scheduled."
 * Parallel groups identify sets of node indices scheduled together with no dependency
 * between them.
 *
 * @param nodes sequential list of workers in temporal dispatch order
 * @param edges directed edges showing completion-to-schedule dependencies
 * @param parallelGroups groups of node indices scheduled in parallel (each group is a list of indices)
 */
public record InvestigationFlowResponse(
    List<FlowNode> nodes,
    List<FlowEdge> edges,
    List<List<Integer>> parallelGroups
) {}
