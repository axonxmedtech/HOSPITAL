package com.hms.service.subscription;

import com.hms.entitlement.PlanLimit;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalPlanSubscription;
import com.hms.entity.Plan;
import com.hms.exception.PlanLimitReachedException;
import com.hms.repository.BedRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalPlanSubscriptionRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.IcuWardRepository;
import com.hms.repository.OtRoomRepository;
import com.hms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanLimitServiceTest {

    HospitalRepository hospitals = mock(HospitalRepository.class);
    HospitalPlanSubscriptionRepository subs = mock(HospitalPlanSubscriptionRepository.class);
    BedRepository beds = mock(BedRepository.class);
    DoctorRepository doctors = mock(DoctorRepository.class);
    UserRepository users = mock(UserRepository.class);
    PlanLimitService svc = new PlanLimitService(hospitals, subs, beds, mock(IcuWardRepository.class), doctors, users,
            mock(OtRoomRepository.class), "9022915865");
    Hospital hospital = new Hospital();

    @BeforeEach
    void setUp() {
        Plan plan = new Plan();
        plan.setName("Basic");
        plan.setLimits(new java.util.HashMap<>(Map.of("BEDS", 10, "DOCTORS", 0)));
        HospitalPlanSubscription s = new HospitalPlanSubscription();
        s.setPlan(plan);
        when(subs.findByHospitalIdAndIsCurrentTrue(1L)).thenReturn(Optional.of(s));
        when(hospitals.findById(1L)).thenReturn(Optional.of(hospital));
    }

    @Test
    void theEleventhBedIsRefusedWithTheSupportNumber() {
        when(beds.countByHospitalId(1L)).thenReturn(10L);
        assertThatThrownBy(() -> svc.requireRoom(1L, PlanLimit.BEDS))
                .isInstanceOf(PlanLimitReachedException.class)
                .hasMessageContaining("Bed limit reached")
                .hasMessageContaining("allows 10 beds")
                .hasMessageContaining("Call 9022915865 to upgrade your plan");
    }

    @Test
    void aBatchSaysHowManyMoreFit() {
        when(beds.countByHospitalId(1L)).thenReturn(8L);
        assertThatThrownBy(() -> svc.requireRoom(1L, PlanLimit.BEDS, 5)).hasMessageContaining("only 2 more can be added");
        assertThatCode(() -> svc.requireRoom(1L, PlanLimit.BEDS, 2)).doesNotThrowAnyException();
    }

    @Test
    void zeroMeansNotIncludedAndAbsentMeansUnlimited() {
        assertThatThrownBy(() -> svc.requireRoom(1L, PlanLimit.DOCTORS))
                .hasMessageContaining("Doctors are not included in your Basic plan");
        when(users.countActiveByHospitalId(1L)).thenReturn(500L);
        assertThatCode(() -> svc.requireRoom(1L, PlanLimit.STAFF_LOGINS)).doesNotThrowAnyException();
    }

    @Test
    void aHospitalOverrideWinsOverThePlan() {
        hospital.getLimitOverrides().put("BEDS", 15);
        when(beds.countByHospitalId(1L)).thenReturn(12L);
        assertThat(svc.limitFor(1L, PlanLimit.BEDS)).isEqualTo(15);
        assertThatCode(() -> svc.requireRoom(1L, PlanLimit.BEDS)).doesNotThrowAnyException();
    }

    @Test
    void planLimitsKeepOnlyKnownNonNegativeKeys() {
        assertThat(PlanLimitService.normalisePlanLimits(Map.of("beds", 5, "WHATEVER", 3))).containsExactly(Map.entry("BEDS", 5));
        assertThatThrownBy(() -> PlanLimitService.normalisePlanLimits(Map.of("BEDS", -1))).hasMessageContaining("cannot be negative");
    }
}
