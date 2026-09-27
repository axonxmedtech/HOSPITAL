package com.hms.service.hospital.icu;

import com.hms.dto.icu.IcuWardRequest;
import com.hms.dto.icu.IcuWardResponse;
import com.hms.entity.Hospital;
import com.hms.exception.ConflictException;
import com.hms.repository.HospitalRepository;
import com.hms.repository.IcuWardRepository;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class IcuWardServiceTest {

    @Autowired private IcuWardService icuWardService;
    @Autowired private IcuWardRepository icuWardRepository;
    @Autowired private HospitalRepository hospitalRepository;

    @MockBean private SecurityContextHelper securityHelper;

    private Long hospitalId;

    private String uniq() { return Long.toString(System.nanoTime()); }

    private Long newHospital() {
        Hospital h = new Hospital();
        h.setName("H-" + uniq());
        h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD", "IPD", "ICU"));
        h.setIsSingleDoctor(false);
        return hospitalRepository.save(h).getId();
    }

    @BeforeEach
    void setUp() {
        hospitalId = newHospital();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserId()).thenReturn(999L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@icu.test");
    }

    @Test
    void createIcuWard_requiresCriticalCareUnitType() {
        IcuWardRequest req = new IcuWardRequest();
        req.setWardName("Invalid Ward " + uniq());
        req.setUnitType("GENERAL");
        req.setBedPrice(new BigDecimal("2500.00"));
        req.setTotalBeds(5);

        assertThatThrownBy(() -> icuWardService.createIcuWard(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("critical care unit type");
    }

    @Test
    void createIcuWard_succeedsForCriticalCareUnitType() {
        IcuWardRequest req = new IcuWardRequest();
        String name = "SICU-" + uniq();
        req.setWardName(name);
        req.setUnitType("SICU");
        req.setBedPrice(new BigDecimal("4500.00"));
        req.setTotalBeds(4);
        req.setFloorNumber(3);

        IcuWardResponse resp = icuWardService.createIcuWard(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getWardName()).isEqualTo(name);
        assertThat(resp.getUnitType()).isEqualTo("SICU");
        assertThat(resp.getUnitTypeLabel()).isEqualTo("Surgical ICU");
        assertThat(resp.getBedPrice()).isEqualByComparingTo("4500.00");
        assertThat(resp.getTotalBeds()).isEqualTo(4);
        assertThat(resp.getAvailableBeds()).isEqualTo(4);
        assertThat(resp.getOccupiedBeds()).isEqualTo(0);
        assertThat(resp.getWardId()).isNotNull();

        // Check persistent entry
        assertThat(icuWardRepository.findByPublicIdAndHospitalId(resp.getPublicId(), hospitalId)).isPresent();
    }

    @Test
    void createIcuWard_rejectsDuplicateName() {
        String name = "MICU-" + uniq();
        IcuWardRequest req = new IcuWardRequest();
        req.setWardName(name);
        req.setUnitType("MICU");
        req.setBedPrice(new BigDecimal("3000.00"));
        req.setTotalBeds(2);

        icuWardService.createIcuWard(req);

        IcuWardRequest dup = new IcuWardRequest();
        dup.setWardName(name);
        dup.setUnitType("MICU");
        dup.setBedPrice(new BigDecimal("3500.00"));
        dup.setTotalBeds(2);

        assertThatThrownBy(() -> icuWardService.createIcuWard(dup))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateIcuWard_updatesPropertiesAndResizesBeds() {
        String name = "NICU-" + uniq();
        IcuWardRequest req = new IcuWardRequest();
        req.setWardName(name);
        req.setUnitType("NICU");
        req.setBedPrice(new BigDecimal("5000.00"));
        req.setTotalBeds(3);

        IcuWardResponse created = icuWardService.createIcuWard(req);

        IcuWardRequest updateReq = new IcuWardRequest();
        updateReq.setWardName(name + "-Renamed");
        updateReq.setUnitType("NICU");
        updateReq.setBedPrice(new BigDecimal("5500.00"));
        updateReq.setTotalBeds(6);
        updateReq.setFloorNumber(2);

        IcuWardResponse updated = icuWardService.updateIcuWard(created.getPublicId(), updateReq);

        assertThat(updated.getWardName()).isEqualTo(name + "-Renamed");
        assertThat(updated.getBedPrice()).isEqualByComparingTo("5500.00");
        assertThat(updated.getTotalBeds()).isEqualTo(6);
        assertThat(updated.getAvailableBeds()).isEqualTo(6);
    }

    @Test
    void deleteIcuWard_removesIcuWard() {
        IcuWardRequest req = new IcuWardRequest();
        req.setWardName("CCU-" + uniq());
        req.setUnitType("CCU");
        req.setBedPrice(new BigDecimal("4000.00"));
        req.setTotalBeds(2);

        IcuWardResponse created = icuWardService.createIcuWard(req);
        assertThat(icuWardRepository.findByPublicIdAndHospitalId(created.getPublicId(), hospitalId)).isPresent();

        icuWardService.deleteIcuWard(created.getPublicId());

        assertThat(icuWardRepository.findByPublicIdAndHospitalId(created.getPublicId(), hospitalId)).isEmpty();
    }
}
