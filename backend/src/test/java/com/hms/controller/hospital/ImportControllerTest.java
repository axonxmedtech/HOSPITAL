package com.hms.controller.hospital;

import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.import_.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportControllerTest {

    @Mock PatientRepository patientRepository;
    @Mock ImportBatchRepository batchRepository;
    @Mock ImportRowErrorRepository rowErrorRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock ImportBatchService batchService;

    private ImportController controller() {
        ImportEngine engine = new ImportEngine(List.of(new PatientImporter(patientRepository)),
                new ColumnMapper(new ImportFieldRegistry()), patientRepository, rowErrorRepository);
        return new ImportController(engine, new WorkbookParser(), new ColumnMapper(new ImportFieldRegistry()),
                new ImportFieldRegistry(), batchService, batchRepository, rowErrorRepository, securityHelper);
    }

    @Test
    void previewIgnoresAHospitalIdColumnInTheFileAndUsesTheJwt() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);

        ParsedSheet sheet = new ParsedSheet("Patients",
                List.of("Name", "hospital_id"),
                List.of(Map.of("Name", "Ramesh", "hospital_id", "999")));

        ImportPreview preview = controller().previewSheet(sheet, Map.of("Name", "name"));

        assertThat(preview.createCount()).isEqualTo(1);
        // hospital_id is not a mappable field, so it is preserved as data, never used as tenancy
        assertThat(preview.unmappedHeaders()).contains("hospital_id");
    }

    @Test
    void rejectsAnUnsupportedFileExtension() {
        MockMultipartFile bad = new MockMultipartFile(
                "file", "patients.exe", "application/octet-stream", new byte[]{1, 2, 3});

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller().upload(bad, "PATIENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx");
    }
}
