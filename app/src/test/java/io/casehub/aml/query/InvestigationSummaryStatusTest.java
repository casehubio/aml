package io.casehub.aml.query;

import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvestigationSummaryStatusTest {

    @Test
    void lifecycleEventsUseTheStatusesConsumedByQueuesAndMetrics() {
        var observer = new InvestigationSummaryObserver();
        observer.summaryService = mock(InvestigationSummaryService.class);
        UUID caseId = UUID.randomUUID();
        for (String status : new String[]{"RUNNING", "WAITING", "FAULTED"}) {
            observer.onCaseLifecycle(CaseLifecycleEvent.of(caseId, "tenant", "action", "event", status,
                    "actor", "engine", "trace"));
        }
        verify(observer.summaryService, times(2)).updateStatus(caseId, "IN_PROGRESS");
        verify(observer.summaryService).updateStatus(caseId, "FAILED");
    }

    @Test
    void migrationRepairsExistingRowsAndPreservesCompletedCases() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:status-" + UUID.randomUUID());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE aml_investigation_summary (status VARCHAR(32))");
            statement.execute("INSERT INTO aml_investigation_summary VALUES ('WAITING'), ('FAULTED'), ('COMPLETED')");
            try (var sql = new InputStreamReader(getClass().getResourceAsStream(
                    "/db/aml-query/migration/V2016__normalize_investigation_status.sql"), StandardCharsets.UTF_8)) {
                org.h2.tools.RunScript.execute(connection, sql);
            }
            try (var rows = statement.executeQuery("SELECT status FROM aml_investigation_summary ORDER BY status")) {
                assertTrue(rows.next()); assertEquals("COMPLETED", rows.getString(1));
                assertTrue(rows.next()); assertEquals("FAILED", rows.getString(1));
                assertTrue(rows.next()); assertEquals("IN_PROGRESS", rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }
}
