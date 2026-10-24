package com.hms.service.platform;

import com.hms.dto.ConsultationStatementDTO;
import com.hms.entity.ConsultationStatement;
import com.hms.repository.ConsultationStatementRepository;
import com.hms.service.RealtimeNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformStatementServiceTest {

    @Mock
    private ConsultationStatementRepository repository;

    @Mock
    private RealtimeNotifier notifier;

    @InjectMocks
    private PlatformStatementService statementService;

    private ConsultationStatement statement;

    @BeforeEach
    void setUp() {
        statement = new ConsultationStatement();
        statement.setId(10L);
        statement.setHospitalType("HOSPITAL");
        statement.setCategory("MEDICINE_INSTRUCTION");
        statement.setEnglishText("Take with water");
        statement.setMarathiText("पाण्यासोबत घ्या");
        statement.setHindiText("पानी के साथ लें");
        statement.setDisplayOrder(1);
        statement.setIsActive(true);
    }

    @Test
    void getStatementsByType_returnsList() {
        when(repository.findByHospitalTypeOrAllOrderByIdAsc("HOSPITAL")).thenReturn(List.of(statement));

        List<ConsultationStatement> list = statementService.getStatementsByType("HOSPITAL");
        assertEquals(1, list.size());
        assertEquals("Take with water", list.get(0).getEnglishText());
    }

    @Test
    void createStatement_validatesAndSaves() {
        ConsultationStatementDTO dto = new ConsultationStatementDTO();
        dto.setCategory("MEDICINE_INSTRUCTION");
        dto.setEnglishText("At bedtime");
        dto.setMarathiText("झोपण्यापूर्वी घ्या");
        dto.setHindiText("सोते समय लें");
        dto.setDisplayOrder(2);
        dto.setIsActive(true);

        when(repository.save(any(ConsultationStatement.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsultationStatement saved = statementService.createStatement("HOSPITAL", dto);
        assertNotNull(saved);
        assertEquals("HOSPITAL", saved.getHospitalType());
        assertEquals("At bedtime", saved.getEnglishText());
        assertEquals("झोपण्यापूर्वी घ्या", saved.getMarathiText());
        assertEquals("सोते समय लें", saved.getHindiText());
        verify(notifier).refreshAllTenants();
    }

    @Test
    void updateStatement_modifiesFields() {
        when(repository.findById(10L)).thenReturn(Optional.of(statement));
        when(repository.save(any(ConsultationStatement.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsultationStatementDTO dto = new ConsultationStatementDTO();
        dto.setEnglishText("Take with warm water");

        ConsultationStatement updated = statementService.updateStatement(10L, "HOSPITAL", dto);
        assertEquals("Take with warm water", updated.getEnglishText());
        assertEquals("पाण्यासोबत घ्या", updated.getMarathiText());
        verify(notifier).refreshAllTenants();
    }

    @Test
    void deleteStatement_removesEntity() {
        when(repository.findById(10L)).thenReturn(Optional.of(statement));

        statementService.deleteStatement(10L, "HOSPITAL");
        verify(repository).delete(statement);
        verify(notifier).refreshAllTenants();
    }
}
