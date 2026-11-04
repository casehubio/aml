package io.casehub.aml.mcp;

import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class AmlAuditApiTenantTest {
    @Test
    void mcpAuditReadsOnlyTheActiveTenant() {
        final AmlAuditApiImpl api = new AmlAuditApiImpl();
        api.ledgerEntryRepository = mock(LedgerEntryRepository.class);
        api.principal = mock(CurrentPrincipal.class);
        when(api.principal.tenancyId()).thenReturn("tenant-b");
        final UUID caseId = UUID.randomUUID();

        api.getAuditTrail(caseId);

        verify(api.ledgerEntryRepository).findBySubjectId(caseId, "tenant-b");
        verifyNoMoreInteractions(api.ledgerEntryRepository);
    }
}
