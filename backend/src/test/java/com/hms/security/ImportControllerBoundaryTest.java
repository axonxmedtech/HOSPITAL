package com.hms.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hms.controller.hospital.PatientImportController;
import com.hms.entitlement.ControllerModules;
import com.hms.entitlement.EntitlementRegistry;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

/** The import controller's structural guarantees: no repository fields, admin-only at class level, declared as CORE. */
class ImportControllerBoundaryTest {

    @Test
    void theControllerHoldsNoRepository() {
        assertThat(Arrays.stream(PatientImportController.class.getDeclaredFields()).map(Field::getType).map(Class::getName))
                .noneMatch(n -> n.contains(".repository."));
    }

    @Test
    void itIsHospitalAdminOnlyAtClassLevelOnThePatientPaths() {
        PreAuthorize pre = PatientImportController.class.getAnnotation(PreAuthorize.class);
        assertThat(pre).isNotNull();
        assertThat(pre.value()).isEqualTo("hasRole('HOSPITAL_ADMIN')");
        assertThat(PatientImportController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/hospital/patients/import", "/clinic/patients/import");
    }

    @Test
    void itIsDeclaredUnderCoreLikePatientRegistration() {
        assertThat(ControllerModules.moduleOf("PatientImportController")).isEqualTo(EntitlementRegistry.CORE);
        assertThat(ControllerModules.moduleOf("PatientController")).isEqualTo(EntitlementRegistry.CORE);
    }
}
