package com.hms.entity.import_;

import com.hms.repository.import_.ImportBatchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for P0-01 (Hibernate generated-column conflict on import_batches.active_marker).
 *
 * <p>Flyway V24 creates {@code active_marker} on {@code import_batches} as a MySQL VIRTUAL generated column
 * with a uniqueness index {@code uk_import_batch_active (hospital_id, file_sha256, active_marker)}.
 *
 * <p>When {@code active_marker} was mapped as a persistent property in {@link ImportBatch}, Hibernate's
 * schema migration tool ({@code ddl-auto=update}) in production detected a type mismatch between Java
 * {@code Integer} and MySQL {@code TINYINT}, and issued:
 * <pre>
 *   ALTER TABLE import_batches MODIFY COLUMN active_marker INTEGER;
 * </pre>
 * which MySQL 8 rejected with:
 * <pre>
 *   ERROR 3106 (HY000): Changing the STORED status is not supported for generated columns.
 * </pre>
 *
 * <p>This test verifies:
 * <ul>
 *   <li>{@code active_marker} / {@code activeMarker} is NOT a mapped JPA attribute on {@link ImportBatch}.</li>
 *   <li>Hibernate's metamodel does not recognize it as a persistent field, preventing any DDL generation.</li>
 *   <li>{@link ImportBatch} persists and retrieves cleanly via JPA without mapping conflicts.</li>
 * </ul>
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ImportBatchSchemaCompatibilityTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ImportBatchRepository batchRepository;

    @Test
    void activeMarkerIsNotAMappedJpaAttributeOnImportBatch() {
        EntityType<ImportBatch> entityType = entityManager.getMetamodel().entity(ImportBatch.class);
        Set<String> attributeNames = entityType.getAttributes().stream()
                .map(Attribute::getName)
                .collect(Collectors.toSet());

        // Must NOT be mapped: presence causes Hibernate ddl-auto=update to attempt ALTER TABLE on MySQL generated column
        assertThat(attributeNames)
                .as("ImportBatch must not map activeMarker as a persistent attribute to avoid MySQL generated column DDL conflict")
                .doesNotContain("activeMarker", "active_marker");

        // Essential attributes must all be present
        assertThat(attributeNames).contains(
                "id", "publicId", "hospitalId", "entityType", "sourceFilename",
                "fileSha256", "status", "createdCount", "createdAt"
        );
    }

    @Test
    void importBatchPersistsAndLoadsWithoutActiveMarkerMapping() {
        ImportBatch batch = new ImportBatch();
        batch.setHospitalId(101L);
        batch.setFileSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        batch.setStatus(ImportStatus.RUNNING);
        batch.setCreatedBy("test-operator@hospital.test");

        ImportBatch saved = batchRepository.saveAndFlush(batch);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull();

        entityManager.clear();

        ImportBatch reloaded = batchRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getHospitalId()).isEqualTo(101L);
        assertThat(reloaded.getStatus()).isEqualTo(ImportStatus.RUNNING);
        assertThat(reloaded.getFileSha256()).isEqualTo(batch.getFileSha256());
    }
}
