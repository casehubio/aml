package io.casehub.aml.engine;

import io.casehub.aml.ledger.AmlCaseOpenedLedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.merkle.InclusionProof;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmlAuditTenantTest {
    final UUID caseId = UUID.randomUUID();
    final UUID entryId = UUID.randomUUID();
    final LedgerEntryRepository repository = mock(LedgerEntryRepository.class);
    final LedgerVerificationService verification = mock(LedgerVerificationService.class);
    final CurrentPrincipal principal = mock(CurrentPrincipal.class);
    final AmlAuditTrailResource resource = new AmlAuditTrailResource();

    @BeforeEach
    void setUp() {
        resource.ledgerEntryRepository = repository;
        resource.verificationService = verification;
        resource.principal = principal;
        when(principal.tenancyId()).thenReturn("tenant-b");
    }

    @Test
    void auditReadsOnlyTheActiveTenant() {
        final AmlCaseOpenedLedgerEntry ownEntry = new AmlCaseOpenedLedgerEntry();
        ownEntry.id = entryId;
        ownEntry.entryType = io.casehub.ledger.api.model.LedgerEntryType.EVENT;
        ownEntry.transactionId = "tx-b";
        ownEntry.originAccountId = "origin-b";
        ownEntry.destinationAccountId = "destination-b";
        when(repository.findBySubjectId(caseId, "tenant-b")).thenReturn(List.of(ownEntry));

        final var entries = resource.getAuditTrail(caseId);

        assertEquals(List.of(entryId), entries.stream().map(e -> e.entryId()).toList());
        verify(repository).findBySubjectId(caseId, "tenant-b");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void proofForAnEntryOutsideTheActiveTenantIsNotReturned() {
        when(repository.findBySubjectId(caseId, "tenant-b")).thenReturn(List.of());
        assertEquals(404, resource.getInclusionProof(caseId, entryId).getStatus());
        verifyNoInteractions(verification);
    }

    @Test
    void proofIsGeneratedInTheSameTenantAsTheOwnershipCheck() {
        final AmlCaseOpenedLedgerEntry entry = new AmlCaseOpenedLedgerEntry();
        entry.id = entryId;
        when(repository.findBySubjectId(caseId, "tenant-b")).thenReturn(List.of(entry));
        when(verification.inclusionProof(entryId, "tenant-b"))
                .thenReturn(new InclusionProof(entryId, 0, 1, "leaf", List.of(), "root"));
        assertEquals(200, resource.getInclusionProof(caseId, entryId).getStatus());
        verify(verification).inclusionProof(entryId, "tenant-b");
    }
}
