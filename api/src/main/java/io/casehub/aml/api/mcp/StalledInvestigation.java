package io.casehub.aml.api.mcp;

import java.time.Duration;
import java.util.UUID;

public record StalledInvestigation(UUID caseId, String waitingForWorkId, Duration stalledFor) {}
