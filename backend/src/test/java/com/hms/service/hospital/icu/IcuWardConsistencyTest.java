package com.hms.service.hospital.icu;

import com.hms.dto.*;
import com.hms.dto.icu.*;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.WardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transactions, isolated database; audit failure occurs after both representations are written. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:icu-consistency;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class IcuWardConsistencyTest {
    @Autowired WardService wards;
    @Autowired com.hms.controller.hospital.NurseController nurseController;
    @Autowired IcuWardService icu;
    @Autowired WardRepository wardRepository;
    @Autowired IcuWardRepository icuRepository;
    @Autowired HospitalRepository hospitals;
    @Autowired NurseProfileRepository nurses;
    @Autowired BedRepository beds;
    @Autowired IcuStayRepository stays;
    @Autowired IpdAdmissionRepository admissions;
    @Autowired IpdBedHistoryRepository histories;
    @MockBean SecurityContextHelper security;
    @MockBean AuditLogService audit;
    Long hospitalId;

    @BeforeEach
    void setup() {
        Hospital h = new Hospital();
        h.setName("Consistency");
        h.setCustomId("C-" + System.nanoTime());
        h.setIsActive(true);
        h.setSubscriptionStatus("ACTIVE");
        h.setModules(List.of("ICU", "IPD", "NURSING"));
        hospitalId = hospitals.save(h).getId();
        when(security.getCurrentHospitalId()).thenReturn(hospitalId);
        when(security.getCurrentUserRole()).thenReturn("HOSPITAL_ADMIN");
    }

    IcuWardRequest request() {
        IcuWardRequest r = new IcuWardRequest();
        r.setWardName("ICU-" + System.nanoTime());
        r.setUnitType("ICU");
        r.setBedPrice(BigDecimal.TEN);
        r.setTotalBeds(1);
        r.setFloorNumber(2);
        return r;
    }

    Long nurse() {
        NurseProfile n = new NurseProfile();
        n.setHospitalId(hospitalId);
        n.setName("Incharge");
        n.setEmail("n-" + System.nanoTime() + "@test.invalid");
        n.setIsIncharge(true);
        return nurses.save(n).getId();
    }

    void assertBoth(IcuWardResponse w, String name, BigDecimal price, Long incharge) {
        Ward base = wardRepository.findByWardIdAndHospitalId(w.getWardId(), hospitalId).orElseThrow();
        IcuWard extension = icuRepository.findByPublicIdAndHospitalId(w.getPublicId(), hospitalId).orElseThrow();
        assertThat(base.getWardName()).isEqualTo(name);
        assertThat(extension.getWardName()).isEqualTo(name);
        assertThat(base.getBedPrice()).isEqualByComparingTo(price);
        assertThat(extension.getBedPrice()).isEqualByComparingTo(price);
        assertThat(base.getInchargeNurseId()).isEqualTo(incharge);
        assertThat(extension.getInchargeNurseId()).isEqualTo(incharge);
        assertThat(extension.getFloorNumber()).isEqualTo(base.getFloorNumber());
        assertThat(extension.getTotalBeds()).isEqualTo(base.getTotalBeds());
    }

    @Test void generalUpdateAndNursingAssignmentStillWork() {
        CreateWardRequest r = new CreateWardRequest();
        r.setWardName("General"); r.setBedPrice(BigDecimal.TEN); r.setTotalBeds(1);
        Long id = wards.createWard(r).getWardId();
        UpdateWardRequest update = new UpdateWardRequest(); update.setWardName("Renamed");
        assertThat(wards.updateWard(id, update).getWardName()).isEqualTo("Renamed");
        Long nurseId = nurse();
        icu.setWardIncharge(id, nurseId);
        assertThat(wardRepository.findByWardIdAndHospitalId(id, hospitalId).orElseThrow().getInchargeNurseId()).isEqualTo(nurseId);
    }

    @Test void genericIcuUpdateAndAssignmentAreRejectedWithoutWrites() {
        IcuWardResponse w = icu.createIcuWard(request());
        UpdateWardRequest update = new UpdateWardRequest();
        update.setWardName("Bad"); update.setBedPrice(BigDecimal.ONE); update.setTotalBeds(3);
        assertThatThrownBy(() -> wards.updateWard(w.getWardId(), update)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dedicated ICU");
        assertThatThrownBy(() -> wards.setIncharge(w.getWardId(), nurse())).isInstanceOf(IllegalArgumentException.class);
        assertBoth(w, w.getWardName(), BigDecimal.TEN, null);
        assertThat(beds.findByWardIdAndHospitalId(w.getWardId(), hospitalId)).hasSize(1);
    }

    @Test void dedicatedUpdateSynchronizesActualBaseValues() {
        IcuWardRequest r = request();
        IcuWardResponse w = icu.createIcuWard(r);
        r.setWardName("Changed"); r.setBedPrice(BigDecimal.ONE); r.setTotalBeds(2); r.setFloorNumber(null);
        icu.updateIcuWard(w.getPublicId(), r);
        assertBoth(w, "Changed", BigDecimal.ONE, null);
        assertThat(wardRepository.findByWardIdAndHospitalId(w.getWardId(), hospitalId).orElseThrow().getFloorNumber()).isEqualTo(2);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "HOSPITAL_ADMIN")
    void nursingEntryAssignsAndRemovesBothIncharges() {
        IcuWardResponse w = icu.createIcuWard(request());
        Long nurseId = nurse();
        SetWardInchargeRequest request = new SetWardInchargeRequest();
        request.setWardId(w.getWardId()); request.setInchargeNurseProfileId(nurseId);
        nurseController.setWardIncharge(request);
        assertBoth(w, w.getWardName(), BigDecimal.TEN, nurseId);
        request.setInchargeNurseProfileId(null);
        nurseController.setWardIncharge(request);
        assertBoth(w, w.getWardName(), BigDecimal.TEN, null);
        verify(audit, times(2)).logAction(eq("WARD_INCHARGE_SET"), anyString(), any(), eq(hospitalId), eq("WARD"), eq(w.getWardId().toString()), isNull());
    }

    @Test void genericDeleteCannotOrphanEvenAnEmptyIcuWard() {
        IcuWardResponse w = icu.createIcuWard(request());
        assertThatThrownBy(() -> wards.deleteWard(w.getWardId())).isInstanceOf(IllegalArgumentException.class);
        assertBoth(w, w.getWardName(), BigDecimal.TEN, null);
        icu.deleteIcuWard(w.getPublicId());
        assertThat(wardRepository.findByWardIdAndHospitalId(w.getWardId(), hospitalId)).isEmpty();
        assertThat(icuRepository.findByPublicIdAndHospitalId(w.getPublicId(), hospitalId)).isEmpty();
    }

    @Test void activeAndHistoricalClinicalRecordsSurviveBothDeletePaths() {
        IcuWardResponse w = icu.createIcuWard(request());
        IcuStay stay = new IcuStay();
        stay.setHospitalId(hospitalId); stay.setWardId(w.getWardId()); stay.setIpdAdmissionId(800L);
        stay.setPatientId(900L); stay.setStatus("ACTIVE"); stay.setSource("WARD"); stay.setAdmittedAt(LocalDateTime.now());
        stays.save(stay);
        assertThatThrownBy(() -> wards.deleteWard(w.getWardId())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> icu.deleteIcuWard(w.getPublicId())).isInstanceOf(com.hms.exception.ConflictException.class);
        stay.setStatus("CLOSED"); stay.setDischargedAt(LocalDateTime.now()); stays.save(stay);
        assertThatThrownBy(() -> wards.deleteWard(w.getWardId())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> icu.deleteIcuWard(w.getPublicId())).isInstanceOf(com.hms.exception.ConflictException.class);
        assertThat(stays.findByPublicIdAndHospitalId(stay.getPublicId(), hospitalId)).isPresent();
        assertBoth(w, w.getWardName(), BigDecimal.TEN, null);
    }

    @Test void foreignIdsFailClosedAcrossEveryEntryPath() {
        IcuWardResponse w = icu.createIcuWard(request());
        when(security.getCurrentHospitalId()).thenReturn(hospitalId + 10000);
        assertThatThrownBy(() -> wards.updateWard(w.getWardId(), new UpdateWardRequest())).isInstanceOf(com.hms.exception.ResourceNotFoundException.class);
        assertThatThrownBy(() -> wards.deleteWard(w.getWardId())).isInstanceOf(com.hms.exception.ResourceNotFoundException.class);
        assertThatThrownBy(() -> icu.setWardIncharge(w.getWardId(), null)).isInstanceOf(com.hms.exception.ResourceNotFoundException.class);
        assertThatThrownBy(() -> icu.updateIcuWard(w.getPublicId(), request())).isInstanceOf(com.hms.exception.ResourceNotFoundException.class);
        assertThatThrownBy(() -> icu.deleteIcuWard(w.getPublicId())).isInstanceOf(com.hms.exception.ResourceNotFoundException.class);
        when(security.getCurrentHospitalId()).thenReturn(hospitalId);
        assertBoth(w, w.getWardName(), BigDecimal.TEN, null);
    }

    @Test void failureAfterBothWritesRollsBackMetadataAndBedResize() {
        IcuWardRequest r = request();
        IcuWardResponse w = icu.createIcuWard(r);
        r.setWardName("Rollback"); r.setBedPrice(BigDecimal.ONE); r.setTotalBeds(2);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).logAction(eq("ICU_WARD_UPDATED"), anyString(), any(), anyLong(), anyString(), anyString(), isNull());
        assertThatThrownBy(() -> icu.updateIcuWard(w.getPublicId(), r)).isInstanceOf(IllegalStateException.class);
        assertBoth(w, w.getWardName(), BigDecimal.TEN, null);
        assertThat(beds.findByWardIdAndHospitalId(w.getWardId(), hospitalId)).hasSize(1);
    }

    @Test void failureAfterDeleteRollsBackBothWardRecordsAndBeds() {
        IcuWardResponse w = icu.createIcuWard(request());
        doThrow(new IllegalStateException("audit unavailable")).when(audit).logAction(eq("ICU_WARD_DELETED"), anyString(), any(), anyLong(), anyString(), anyString(), isNull());
        assertThatThrownBy(() -> icu.deleteIcuWard(w.getPublicId())).isInstanceOf(IllegalStateException.class);
        assertBoth(w, w.getWardName(), BigDecimal.TEN, null);
        assertThat(beds.findByWardIdAndHospitalId(w.getWardId(), hospitalId)).hasSize(1);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "NURSE_INCHARGE")
    void nursingEntryRemainsAdminOnly() {
        IcuWardResponse w = icu.createIcuWard(request());
        SetWardInchargeRequest r = new SetWardInchargeRequest();
        r.setWardId(w.getWardId());
        assertThatThrownBy(() -> nurseController.setWardIncharge(r)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertBoth(w, w.getWardName(), BigDecimal.TEN, null);
    }

    @Test void invalidAndForeignNurseProfilesCannotChangeEitherRepresentation() {
        IcuWardResponse w = icu.createIcuWard(request());
        Long nurseId = nurse();
        NurseProfile n = nurses.findByIdAndHospitalId(nurseId, hospitalId).orElseThrow();
        n.setIsActive(false); nurses.save(n);
        assertThatThrownBy(() -> icu.setWardIncharge(w.getWardId(), nurseId)).isInstanceOf(IllegalArgumentException.class);
        n.setIsActive(true); n.setIsIncharge(false); nurses.save(n);
        assertThatThrownBy(() -> icu.setWardIncharge(w.getWardId(), nurseId)).isInstanceOf(IllegalArgumentException.class);
        n.setIsIncharge(true); n.setHospitalId(hospitalId + 10000); nurses.save(n);
        assertThatThrownBy(() -> icu.setWardIncharge(w.getWardId(), nurseId)).isInstanceOf(IllegalArgumentException.class);
        assertBoth(w, w.getWardName(), BigDecimal.TEN, null);
    }
}
