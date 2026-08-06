package io.casehub.aml.metrics;

import io.casehub.aml.quality.*;
import io.casehub.ledger.api.model.AttestationVerdict;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SarQualityServiceTest {

    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks SarQualityService service;

    @Test
    void noAttestations_emptyReport() {
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        var report = service.generateReport();
        assertEquals(0, report.totalCases());
        assertEquals(0, report.seeded().total());
        assertEquals(0, report.unseeded().total());
    }

    @Test
    void mixedOutcomes_correctSegmentation() {
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(
                new Object[]{true,  AttestationVerdict.SOUND,   1, 5L},
                new Object[]{true,  AttestationVerdict.FLAGGED, 1, 2L},
                new Object[]{false, AttestationVerdict.SOUND,   0, 3L},
                new Object[]{false, AttestationVerdict.FLAGGED, 0, 4L}
        ));
        var report = service.generateReport();
        assertEquals(14, report.totalCases());
        assertEquals(7, report.seeded().total());
        assertEquals(5, report.seeded().upheld());
        assertEquals(2, report.seeded().notUpheld());
        assertEquals(7, report.unseeded().total());
        assertEquals(3, report.unseeded().upheld());
        assertEquals(4, report.unseeded().notUpheld());
    }
}
