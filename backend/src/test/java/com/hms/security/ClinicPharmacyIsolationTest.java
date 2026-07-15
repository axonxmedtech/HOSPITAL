package com.hms.security;

import com.hms.entity.HospitalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0B - the standing guard for Clinic and Pharmacy.
 *
 * Tenant isolation is not a property of any single class: it is a property of WHICH
 * controllers opt into a /clinic or /pharmacy path alias. Nothing else enforces it, so
 * this test freezes that surface. A new alias, or a hospital-only feature leaking onto
 * one, fails here rather than in a clinic's production tenant.
 *
 * If a change here is intentional, update the golden set deliberately -- that edit is
 * the review gate.
 */
class ClinicPharmacyIsolationTest {

    private static final String CONTROLLER_PACKAGE = "com.hms.controller";

    /**
     * Controllers Clinic and/or Pharmacy are allowed to reach. Frozen on 2026-07-10.
     * Adding a row means a hospital feature is now reachable by another tenant type.
     *
     * The com.hms.controller.pharmacy.* entries are the standalone Pharmacy module and
     * own the /pharmacy namespace outright; the rest are hospital controllers that opt
     * into a /clinic or /pharmacy alias.
     */
    private static final Set<String> ALIASED_CONTROLLERS = new TreeSet<>(Set.of(
            // standalone Pharmacy module
            "InventoryController",
            "ManufacturerController",
            "MedicineCategoryController",
            "MedicineMasterController",
            "PharmacyBranchController",
            "PharmacyReportsController",
            "PharmacySaleController",
            "PurchaseController",
            "SupplierController",
            // hospital controllers shared with Clinic / Pharmacy
            "AppointmentController",
            "BedController",
            "BillingController",
            "ConsultationNotePresetController",
            "DoctorController",
            "FaqController",
            "FormAccessController",
            "HospitalAuditController",
            "HospitalAuthController",
            "HospitalFeeController",
            "HospitalInventoryController",
            "HospitalServiceController",
            "HospitalStatsController",
            "HospitalTicketController",
            "IpdAdmissionController",
            "MedicineController",
            "OpdController",
            "PatientController",
            "PharmacistController",
            "PharmacyController",
            "PrescriptionPresetController",
            "ReceptionistController",
            "VitalSettingsController",
            "WardController"));

    /** OT is a hospital-only capability. These must never be reachable by Clinic/Pharmacy. */
    private static final Set<String> OT_CONTROLLERS = Set.of(
            "SurgeryController", "SurgeryFormController", "OtInchargeController", "OtPermissionController",
            "OtRoomController", "OtPolicyController", "SurgeryTeamController", "SurgeryExecutionController", "RecoveryController");

    private Set<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            try {
                classes.add(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return classes;
    }

    /** Every path this controller serves, from the class mapping and every handler method. */
    private Set<String> pathsOf(Class<?> type) {
        Set<String> paths = new LinkedHashSet<>();
        RequestMapping onClass = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
        if (onClass != null) paths.addAll(Arrays.asList(onClass.value()));
        for (Method m : type.getDeclaredMethods()) {
            RequestMapping onMethod = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
            if (onMethod != null) paths.addAll(Arrays.asList(onMethod.value()));
        }
        return paths;
    }

    private boolean isAliased(Class<?> type) {
        return pathsOf(type).stream().anyMatch(p -> p.startsWith("/clinic") || p.startsWith("/pharmacy"));
    }

    @Test
    void theSetOfClinicAndPharmacyReachableControllers_isFrozen() {
        Set<String> actual = new TreeSet<>();
        for (Class<?> c : controllers()) {
            if (isAliased(c)) actual.add(c.getSimpleName());
        }
        assertThat(actual)
                .as("A controller gained or lost a /clinic or /pharmacy alias. If deliberate, update "
                        + "ALIASED_CONTROLLERS -- that edit is the review gate for tenant isolation.")
                .isEqualTo(ALIASED_CONTROLLERS);
    }

    @Test
    void noClinicOrPharmacyReachableController_isMarkedHospitalOnly() {
        for (Class<?> c : controllers()) {
            if (!isAliased(c)) continue;
            TenantType tenantType = AnnotatedElementUtils.findMergedAnnotation(c, TenantType.class);
            assertThat(tenantType)
                    .as("%s serves /clinic or /pharmacy but is annotated @TenantType, which would 403 "
                            + "those tenants", c.getSimpleName())
                    .isNull();
        }
    }

    @Test
    void everyOtController_isHospitalOnly_andNeverAliased() {
        for (Class<?> c : controllers()) {
            if (!OT_CONTROLLERS.contains(c.getSimpleName())) continue;

            assertThat(isAliased(c))
                    .as("%s must not serve /clinic or /pharmacy: OT is hospital-only", c.getSimpleName())
                    .isFalse();

            TenantType tenantType = AnnotatedElementUtils.findMergedAnnotation(c, TenantType.class);
            assertThat(tenantType).as("%s must carry @TenantType", c.getSimpleName()).isNotNull();
            assertThat(tenantType.value()).containsExactly(HospitalType.HOSPITAL);
        }
    }

    /** Guards against an OT controller being added without the isolation annotations. */
    @Test
    void allThreeOtControllers_exist() {
        Set<String> found = new TreeSet<>();
        for (Class<?> c : controllers()) {
            if (OT_CONTROLLERS.contains(c.getSimpleName())) found.add(c.getSimpleName());
        }
        assertThat(found).isEqualTo(new TreeSet<>(OT_CONTROLLERS));
    }
}
