package com.hms.service.import_;

import com.hms.entity.ImportRowError;
import com.hms.repository.ImportRowErrorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportErrorCsvTest {

    @Mock ImportRowErrorRepository rowErrorRepository;

    @Test
    void producesReuploadableRowsWithATrailingErrorColumn() {
        when(rowErrorRepository.findByBatchIdOrderByRowNumberAsc(17L)).thenReturn(List.of(
                new ImportRowError(17L, 5, "DOB", "Could not read \"31-02-2020\" as a date",
                        "{\"Name\":\"Sita\",\"DOB\":\"31-02-2020\"}")));

        String csv = ImportCsvWriter.write(rowErrorRepository.findByBatchIdOrderByRowNumberAsc(17L),
                List.of("Name", "DOB"));

        assertThat(csv.lines().toList().get(0)).isEqualTo("Name,DOB,_error");
        assertThat(csv).contains("Sita");
        assertThat(csv).contains("31-02-2020");
        assertThat(csv).contains("DOB: Could not read");
    }

    @Test
    void neutralisesFormulaInjection() {
        when(rowErrorRepository.findByBatchIdOrderByRowNumberAsc(17L)).thenReturn(List.of(
                new ImportRowError(17L, 2, "Name", "bad", "{\"Name\":\"=cmd|'/c calc'!A1\"}")));

        String csv = ImportCsvWriter.write(rowErrorRepository.findByBatchIdOrderByRowNumberAsc(17L),
                List.of("Name"));

        assertThat(csv).doesNotContain(",=cmd").doesNotContain("\n=cmd");
        assertThat(csv).contains("'=cmd");
    }
}
