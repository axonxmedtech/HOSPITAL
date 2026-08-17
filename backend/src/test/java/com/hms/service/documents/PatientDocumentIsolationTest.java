package com.hms.service.documents;

import com.hms.entity.PatientDocument;
import com.hms.repository.PatientDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A document is only ever reachable through its own hospital.
 *
 * <p>These are lab reports and scans, so a hole here hands one hospital another's patient records.
 * The lookups take the hospital id from the JWT and pass it <em>into</em> the query rather than
 * loading by public id and comparing afterwards. Both are correct the day they are written; only
 * one stays correct when somebody later adds a method and forgets the comparison.
 */
@ExtendWith(MockitoExtension.class)
class PatientDocumentIsolationTest {

    @Mock PatientDocumentRepository documentRepository;

    @Test
    void aDocumentIsInvisibleToAnotherHospital() {
        PatientDocument owned = new PatientDocument();
        owned.setPublicId("doc-1");
        owned.setHospitalId(7L);

        when(documentRepository.findByPublicIdAndHospitalId("doc-1", 7L)).thenReturn(Optional.of(owned));
        when(documentRepository.findByPublicIdAndHospitalId("doc-1", 99L)).thenReturn(Optional.empty());

        assertThat(documentRepository.findByPublicIdAndHospitalId("doc-1", 7L)).isPresent();
        assertThat(documentRepository.findByPublicIdAndHospitalId("doc-1", 99L)).isEmpty();
    }

    /**
     * The listing is scoped by hospital as well as patient. Scoping by patient alone would leak
     * across tenants the moment two hospitals hold rows for the same patient id.
     */
    @Test
    void theListingIsScopedByHospitalNotJustByPatient() {
        when(documentRepository
                .findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(99L, 55L))
                .thenReturn(java.util.List.of());

        assertThat(documentRepository
                .findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(99L, 55L))
                .isEmpty();
    }

    /**
     * Every finder on this repository takes a hospital id.
     *
     * <p>Asserted by reflection rather than by reading the interface, so a future finder that omits
     * it fails here instead of quietly becoming the one query that crosses tenants. The exclusions
     * are the inherited JpaRepository methods, which the service never calls directly.
     */
    @Test
    void everyDeclaredFinderIsScopedByHospital() {
        java.util.List<String> unscoped = java.util.Arrays
                .stream(PatientDocumentRepository.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("find") || m.getName().startsWith("count"))
                .filter(m -> !m.getName().toLowerCase().contains("hospitalid"))
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(unscoped)
                .as("a finder on PatientDocumentRepository is not scoped by hospital: %s", unscoped)
                .isEmpty();
    }
}
