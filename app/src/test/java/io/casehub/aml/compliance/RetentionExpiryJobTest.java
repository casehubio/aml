package io.casehub.aml.compliance;

import io.casehub.ledger.api.model.ErasureReason;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RetentionExpiryJobTest {

    @Test
    void processRetention_erases_each_actor_with_retention_expired_reason() {
        final AmlErasureService erasureService = Mockito.mock(AmlErasureService.class);
        when(erasureService.erase(eq("officer-1"), eq(ErasureReason.RETENTION_EXPIRED)))
                .thenReturn(new ActorErasureResult("officer-1", true, 3L, UUID.randomUUID()));
        when(erasureService.erase(eq("officer-2"), eq(ErasureReason.RETENTION_EXPIRED)))
                .thenReturn(new ActorErasureResult("officer-2", true, 7L, UUID.randomUUID()));

        final RetentionExpiryJob job = new RetentionExpiryJob(erasureService, 2555);

        job.processRetention(List.of("officer-1", "officer-2"));

        verify(erasureService).erase("officer-1", ErasureReason.RETENTION_EXPIRED);
        verify(erasureService).erase("officer-2", ErasureReason.RETENTION_EXPIRED);
        verifyNoMoreInteractions(erasureService);
    }

    @Test
    void processRetention_with_empty_list_does_not_call_erase() {
        final AmlErasureService erasureService = Mockito.mock(AmlErasureService.class);

        final RetentionExpiryJob job = new RetentionExpiryJob(erasureService, 2555);

        job.processRetention(List.of());

        verifyNoInteractions(erasureService);
    }

    @Test
    void processRetention_continues_after_erasure_failure() {
        final AmlErasureService erasureService = Mockito.mock(AmlErasureService.class);
        when(erasureService.erase(eq("actor-bad"), eq(ErasureReason.RETENTION_EXPIRED)))
                .thenThrow(new RuntimeException("DB connection lost"));
        when(erasureService.erase(eq("actor-good"), eq(ErasureReason.RETENTION_EXPIRED)))
                .thenReturn(new ActorErasureResult("actor-good", true, 2L, UUID.randomUUID()));

        final RetentionExpiryJob job = new RetentionExpiryJob(erasureService, 2555);

        assertDoesNotThrow(() -> job.processRetention(List.of("actor-bad", "actor-good")));

        verify(erasureService).erase("actor-bad", ErasureReason.RETENTION_EXPIRED);
        verify(erasureService).erase("actor-good", ErasureReason.RETENTION_EXPIRED);
    }

    @Test
    void processRetention_handles_already_erased_actors_gracefully() {
        final AmlErasureService erasureService = Mockito.mock(AmlErasureService.class);
        when(erasureService.erase(eq("already-erased"), eq(ErasureReason.RETENTION_EXPIRED)))
                .thenReturn(new ActorErasureResult("already-erased", false, 0L, null));

        final RetentionExpiryJob job = new RetentionExpiryJob(erasureService, 2555);

        job.processRetention(List.of("already-erased"));

        verify(erasureService).erase("already-erased", ErasureReason.RETENTION_EXPIRED);
    }
}
