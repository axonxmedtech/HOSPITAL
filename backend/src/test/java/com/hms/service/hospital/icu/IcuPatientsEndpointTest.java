package com.hms.service.hospital.icu;

import com.hms.dto.icu.IcuPatientSummaryDTO;
import com.hms.dto.IpdAdmissionSummaryDTO;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.IpdAdmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IcuPatientsEndpointTest {

    @Autowired private IcuStayService icuStayService;
    @Autowired private IpdAdmissionService ipdAdmissionService;
    @Autowired private HospitalRepository hospitalRepository;
    @Autowired private WardRepository wardRepository;
    @Autowired private IcuWardRepository icuWardRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private IcuStayRepository icuStayRepository;

    @MockBean private SecurityContextHelper securityHelper;

    private Long hospitalIdA;
    private Long hospitalIdB;

    private String uniq() {
        return Long.toString(System.nanoTime());
    }

    @BeforeEach
    void setUp() {
        Hospital ha = new Hospital();
        ha.setName("Hospital-A-" + uniq());
        ha.setCustomId("HOSP-A-" + uniq());
        ha.setSubscriptionStatus("ACTIVE");
        ha.setIsActive(true);
        ha.setModules(List.of("OPD", "IPD", "ICU"));
        hospitalIdA = hospitalRepository.save(ha).getId();

        Hospital hb = new Hospital();
        hb.setName("Hospital-B-" + uniq());
        hb.setCustomId("HOSP-B-" + uniq());
        hb.setSubscriptionStatus("ACTIVE");
        hb.setIsActive(true);
        hb.setModules(List.of("OPD", "IPD", "ICU"));
        hospitalIdB = hospitalRepository.save(hb).getId();
    }

    @Test
    void icuPatients_segregatesIcuAndGeneralWardsCleanly_andEnforcesTenancy() {
        // 1. Create ICU Ward for Hospital A
        Ward icuWardEntity = new Ward();
        icuWardEntity.setHospitalId(hospitalIdA);
        icuWardEntity.setWardName("MICU-1");
        icuWardEntity.setBedPrice(new BigDecimal("4000"));
        icuWardEntity.setTotalBeds(5);
        Ward savedIcuWard = wardRepository.save(icuWardEntity);

        IcuWard icuWard = new IcuWard();
        icuWard.setPublicId("icuw-" + uniq());
        icuWard.setHospitalId(hospitalIdA);
        icuWard.setWardId(savedIcuWard.getWardId());
        icuWard.setWardName(savedIcuWard.getWardName());
        icuWard.setUnitType(CareUnitRegistry.MICU);
        icuWard.setBedPrice(new BigDecimal("4000"));
        icuWard.setTotalBeds(5);
        icuWardRepository.save(icuWard);

        // 2. Create General Ward for Hospital A
        Ward genWard = new Ward();
        genWard.setHospitalId(hospitalIdA);
        genWard.setWardName("General Male Ward");
        genWard.setBedPrice(new BigDecimal("1000"));
        genWard.setTotalBeds(10);
        Ward savedGenWard = wardRepository.save(genWard);

        // 3. Beds
        Bed icuBed = new Bed();
        icuBed.setHospitalId(hospitalIdA);
        icuBed.setWardId(savedIcuWard.getWardId());
        icuBed.setBedCode("MICU-BED-01");
        icuBed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(icuBed);

        Bed genBed = new Bed();
        genBed.setHospitalId(hospitalIdA);
        genBed.setWardId(savedGenWard.getWardId());
        genBed.setBedCode("GEN-BED-01");
        genBed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(genBed);

        // 4. Doctor
        Doctor doc = new Doctor();
        doc.setHospitalId(hospitalIdA);
        doc.setName("Dr. Sharma");
        doc.setEmail("sharma@hospital.test");
        doc.setPhone("9876543210");
        doc.setSpecialization("Critical Care");
        doc.setIsActive(true);
        Doctor savedDoc = doctorRepository.save(doc);

        Doctor intensivist = new Doctor();
        intensivist.setHospitalId(hospitalIdA);
        intensivist.setName("Dr. Intensivist");
        intensivist.setEmail("intensivist@hospital.test");
        intensivist.setPhone("9876543211");
        intensivist.setSpecialization("Intensivist");
        intensivist.setIsActive(true);
        Doctor savedIntensivist = doctorRepository.save(intensivist);

        // 5. Patients
        Patient patIcu = new Patient();
        patIcu.setHospitalId(hospitalIdA);
        patIcu.setName("ICU Patient John");
        patIcu.setGender("MALE");
        patIcu.setPhone("9900000001");
        patIcu.setDateOfBirth(java.time.LocalDate.of(1966, 1, 1));
        patIcu.setIsActive(true);
        patientRepository.save(patIcu);

        Patient patGen = new Patient();
        patGen.setHospitalId(hospitalIdA);
        patGen.setName("General Patient Jane");
        patGen.setGender("FEMALE");
        patGen.setPhone("9900000002");
        patGen.setDateOfBirth(java.time.LocalDate.of(1992, 1, 1));
        patGen.setIsActive(true);
        patientRepository.save(patGen);

        // 6. Admissions
        IpdAdmission admIcu = new IpdAdmission();
        admIcu.setHospitalId(hospitalIdA);
        admIcu.setIpdNumber("IPD-ICU-001");
        admIcu.setPatientId(patIcu.getId());
        admIcu.setWardId(savedIcuWard.getWardId());
        admIcu.setBedId(icuBed.getBedId());
        admIcu.setDoctorId(savedDoc.getId());
        admIcu.setStatus("ADMITTED");
        admIcu.setAdmissionType("EMERGENCY");
        admIcu.setAdmissionDatetime(LocalDateTime.now().minusHours(5));
        IpdAdmission savedAdmIcu = ipdAdmissionRepository.save(admIcu);

        IcuStay stay = new IcuStay();
        stay.setPublicId("stay-" + uniq());
        stay.setHospitalId(hospitalIdA);
        stay.setIpdAdmissionId(savedAdmIcu.getId());
        stay.setPatientId(patIcu.getId());
        stay.setWardId(savedIcuWard.getWardId());
        stay.setStatus(IcuStay.ACTIVE);
        stay.setSource(IcuStay.SRC_EMERGENCY);
        stay.setAdmissionReason("Acute Respiratory Distress");
        stay.setIntensivistDoctorId(savedIntensivist.getId());
        stay.setAdmittedAt(LocalDateTime.now().minusHours(5));
        icuStayRepository.save(stay);

        IpdAdmission admGen = new IpdAdmission();
        admGen.setHospitalId(hospitalIdA);
        admGen.setIpdNumber("IPD-GEN-001");
        admGen.setPatientId(patGen.getId());
        admGen.setWardId(savedGenWard.getWardId());
        admGen.setBedId(genBed.getBedId());
        admGen.setDoctorId(savedDoc.getId());
        admGen.setStatus("ADMITTED");
        admGen.setAdmissionType("ELECTIVE");
        admGen.setAdmissionDatetime(LocalDateTime.now().minusHours(2));
        ipdAdmissionRepository.save(admGen);

        // Act & Assert for Hospital A:
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalIdA);
        when(securityHelper.getCurrentUserRole()).thenReturn("HOSPITAL_ADMIN");

        // Verify ICU tab endpoint:
        List<IcuPatientSummaryDTO> icuList = icuStayService.getAdmittedIcuPatientsForCurrentUser();
        assertThat(icuList).hasSize(1);
        IcuPatientSummaryDTO icuPatient = icuList.get(0);
        assertThat(icuPatient.getPatientName()).isEqualTo("ICU Patient John");
        assertThat(icuPatient.getIcuWardName()).isEqualTo("MICU-1");
        assertThat(icuPatient.getUnitType()).isEqualTo(CareUnitRegistry.MICU);
        assertThat(icuPatient.getBedNumber()).isEqualTo("MICU-BED-01");
        assertThat(icuPatient.getDoctorName()).isEqualTo("Dr. Sharma");
        assertThat(icuPatient.getIntensivistName()).isEqualTo("Dr. Intensivist");
        assertThat(icuPatient.getAdmissionReason()).isEqualTo("Acute Respiratory Distress");

        // Verify General IPD tab endpoint excludes the ICU patient:
        List<IpdAdmissionSummaryDTO> generalList = ipdAdmissionService.getAdmittedIpdSummariesForCurrentUser();
        assertThat(generalList).hasSize(1);
        assertThat(generalList.get(0).getPatientName()).isEqualTo("General Patient Jane");
        assertThat(generalList.get(0).getWardName()).isEqualTo("General Male Ward");

        // Verify Tenant Isolation: Hospital B cannot see Hospital A's ICU patients
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalIdB);
        List<IcuPatientSummaryDTO> hospitalBList = icuStayService.getAdmittedIcuPatientsForCurrentUser();
        assertThat(hospitalBList).isEmpty();
    }
}
