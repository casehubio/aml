package io.casehub.aml.api.mcp;

import io.casehub.aml.api.model.InvestigationFindingsResponse;
import io.casehub.aml.api.model.InvestigationGatesResponse;
import io.casehub.aml.api.model.InvestigationRoutingResponse;

import java.util.UUID;

public record InvestigationDetail(
        UUID caseId,
        String status,
        String outcome,
        InvestigationFindingsResponse findings,
        InvestigationGatesResponse gates,
        InvestigationRoutingResponse routing
) {}
