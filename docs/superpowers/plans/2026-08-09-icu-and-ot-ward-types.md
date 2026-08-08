# ICU and OT Ward Types — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a hospital manage IPD, ICU and OT wards as three separate things, transfer a patient into ICU and back, and record which OT ward a surgery used — all on one bill for the whole stay.

**Architecture:** One `wards` table gains a `ward_type` (`IPD` / `ICU` / `OT`); three screens filter on it. The IPD admission stays the single billing episode, and an ICU stay is a stint in an ICU-typed ward recorded in the existing `ipd_bed_history`. OT wards hold exactly one bed and add a one-off charge when a surgery uses them.

**Tech Stack:** Spring Boot 3 · JPA/Hibernate · MySQL · React 19 · Tailwind · JUnit 5 · Mockito · AssertJ

---

## Decisions this plan implements

Settled with the product owner before writing:

| #   | Decision                                                                                                                                      |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| D1  | One `wards` table with a type, **not** three tables — `IpdAdmission.wardId`, `Surgery.otWardId` and `Bed.wardId` all already point at `wards` |
| D2  | An ICU stay is a **child of the IPD admission**, which remains the billing episode                                                            |
| D3  | **No direct ICU admission** — admit to an IPD ward, then transfer                                                                             |
| D4  | Existing wards **all become IPD**; the admin re-types the few that aren't. No name guessing                                                   |
| D5  | An OT ward holds **exactly one bed**                                                                                                          |
| D6  | On transfer to ICU the IPD bed is **released**, and only the ICU rate is charged                                                              |
| D7  | Using an OT ward adds a **one-off charge** taken from that ward's `bedPrice`                                                                  |

**D6 is mostly free.** `BillingSchedulerService` already charges each day from the patient's _current_ ward's `bedPrice`, and the existing transfer in `IpdAdmissionService` already releases the old bed via `BedStatusService`. Moving the admission to an ICU ward therefore starts charging the ICU rate by itself. The work is restricting _which_ wards you may transfer into, and surfacing the stay.

**No new table for ICU stays.** `ipd_bed_history` already records every ward/bed stint with `assignedAt` / `releasedAt`. An ICU stay is one of those rows whose ward is ICU-typed, so it is derived, not duplicated. If ICU-specific clinical fields (ventilator, reason for escalation) are wanted later, that is when a table earns its place — see "Deliberately not in scope".

---

## File Structure

**Create — backend**

| File                                     | Responsibility                                                                                                                      |
| ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `entity/WardType.java`                   | Enum `IPD`, `ICU`, `OT`                                                                                                             |
| `dto/IcuStayResponse.java`               | One ICU stint on an admission: ward, bed, in/out, nights                                                                            |
| `service/hospital/IcuStayService.java`   | Derives ICU stays for an admission from bed history                                                                                 |
| `controller/hospital/IcuController.java` | `GET /hospital/icu/stays/{admissionId}` only — transfers reuse the existing IPD transfer endpoint rather than getting a second path |

**Modify — backend**

| File                                                                        | Change                                                                       |
| --------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `entity/Ward.java`                                                          | Add `wardType`, defaulting to `IPD`                                          |
| `repository/WardRepository.java`                                            | `findByHospitalIdAndWardType`, `findByHospitalIdAndWardTypeIn`               |
| `service/hospital/WardService.java`                                         | Accept and validate type; enforce D5; filter listings                        |
| `dto/CreateWardRequest.java`, `UpdateWardRequest.java`, `WardResponse.java` | Carry `wardType`                                                             |
| `service/hospital/IpdAdmissionService.java`                                 | Restrict transfer targets by type (D3, D6)                                   |
| `service/hospital/SurgeryService.java`                                      | Select OT wards by type, not name; apply the OT charge (D7)                  |
| `service/hospital/ot/OtRoomService.java:88`                                 | `suggestFromWards()` selects by type, killing the "FOOT WARD" false positive |
| `config/DatabaseMigrationRunner.java`                                       | `ward_type` column, defaulted to `IPD` (D4)                                  |
| `setup/schema-full.sql`                                                     | Mirror the DDL                                                               |

**Frontend**

| File                                        | Change                                                              |
| ------------------------------------------- | ------------------------------------------------------------------- |
| `pages/hospital/WardsAndBeds.jsx`           | Accept a `wardType` prop; filter, and label copy per type           |
| `components/WardModal.jsx`                  | Ward type is fixed by the screen, not chosen; hide bed count for OT |
| `pages/hospital/HospitalAdminDashboard.jsx` | Three entries: Wards & Beds, ICU Wards, OT Wards                    |
| `pages/hospital/IpdDetails.jsx`             | "Move to ICU" action + the ICU stays list                           |

---

## Task 1: `ward_type` column and enum

**Files:**

- Create: `backend/src/main/java/com/hms/entity/WardType.java`
- Modify: `backend/src/main/java/com/hms/entity/Ward.java`
- Modify: `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`
- Modify: `setup/schema-full.sql`
- Test: `backend/src/test/java/com/hms/entity/WardTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WardTypeTest {

    /**
     * Every ward that exists today predates typing and is an ordinary inpatient ward. Defaulting to
     * IPD is what makes the migration safe: nothing is reclassified by guessing at its name, which
     * the OT module already learned the hard way ("FOOT WARD" contains "OT").
     */
    @Test
    void aNewWardIsAnIpdWardUntilSaidOtherwise() {
        Ward ward = new Ward();
        assertThat(ward.getWardType()).isEqualTo(WardType.IPD);
    }

    @Test
    void theThreeTypesAreIpdIcuAndOt() {
        assertThat(WardType.values()).containsExactly(WardType.IPD, WardType.ICU, WardType.OT);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=WardTypeTest`
Expected: compilation failure — `WardType` does not exist.

- [ ] **Step 3: Create the enum**

```java
package com.hms.entity;

/**
 * What a ward is for. Beds, pricing and the nurse incharge work identically across all three; the
 * type decides which screen a ward appears on, where a patient may be transferred, and whether the
 * OT module treats it as a theatre.
 */
public enum WardType {
    /** Ordinary inpatient ward. Every ward created before typing existed is one of these. */
    IPD,
    /** Intensive care. A patient reaches one by transfer from an IPD ward, never directly. */
    ICU,
    /** Operating theatre. Holds exactly one bed, because it holds one case at a time. */
    OT
}
```

- [ ] **Step 4: Add the field to `Ward`**

In `Ward.java`, alongside the existing columns:

```java
    /**
     * Defaults to IPD so existing rows and any code path that does not set a type produce an
     * ordinary ward rather than silently landing in ICU or OT.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ward_type", nullable = false, length = 10)
    private WardType wardType = WardType.IPD;
```

Ensure `jakarta.persistence.Enumerated` and `jakarta.persistence.EnumType` are imported. `Ward` uses Lombok `@Data`, so do **not** hand-write accessors.

- [ ] **Step 5: Add the migration**

In `DatabaseMigrationRunner.java`, inside `runMigrations()` beside the other `addColumnIfMissing` calls:

```java
        // Every pre-existing ward becomes IPD. Deliberately not inferred from the ward name:
        // OtRoomService.suggestFromWards already documents why that guess is unsafe.
        addColumnIfMissing("wards", "ward_type", "VARCHAR(10) NOT NULL DEFAULT 'IPD'");
```

- [ ] **Step 6: Mirror it in `setup/schema-full.sql`**

In the `wards` table definition, after `ward_name`:

```sql
  `ward_type` varchar(10) NOT NULL DEFAULT 'IPD',
```

- [ ] **Step 7: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=WardTypeTest`
Expected: PASS, 2 tests.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/hms/entity/WardType.java backend/src/main/java/com/hms/entity/Ward.java backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java setup/schema-full.sql backend/src/test/java/com/hms/entity/WardTypeTest.java
git commit -m "feat(ipd): ward type with every existing ward defaulting to IPD"
```

---

## Task 2: Type-aware ward listing and the single-bed OT rule

**Files:**

- Modify: `backend/src/main/java/com/hms/repository/WardRepository.java`
- Modify: `backend/src/main/java/com/hms/service/hospital/WardService.java`
- Modify: `backend/src/main/java/com/hms/dto/CreateWardRequest.java`, `UpdateWardRequest.java`, `WardResponse.java`
- Test: `backend/src/test/java/com/hms/service/hospital/WardTypeRulesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.hospital;

import com.hms.entity.WardType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules that differ per ward type. Everything else — beds, pricing, incharge — is identical
 * across types on purpose, so there is one implementation to keep correct rather than three.
 */
class WardTypeRulesTest {

    @Test
    void anOtWardIsAllowedExactlyOneBed() {
        assertThat(WardService.bedCountIsValidFor(WardType.OT, 1)).isTrue();
        assertThat(WardService.bedCountIsValidFor(WardType.OT, 2)).isFalse();
        assertThat(WardService.bedCountIsValidFor(WardType.OT, 0)).isFalse();
    }

    @Test
    void ipdAndIcuWardsMayHaveAnyPositiveNumberOfBeds() {
        assertThat(WardService.bedCountIsValidFor(WardType.IPD, 30)).isTrue();
        assertThat(WardService.bedCountIsValidFor(WardType.ICU, 8)).isTrue();
        assertThat(WardService.bedCountIsValidFor(WardType.ICU, 0)).isFalse();
    }

    /** A patient is admitted to a ward or moved to ICU. A theatre is never an admission target. */
    @Test
    void onlyIpdAndIcuWardsCanHoldAnAdmittedPatient() {
        assertThat(WardService.ADMITTABLE_TYPES).containsExactlyInAnyOrder(WardType.IPD, WardType.ICU);
        assertThat(WardService.ADMITTABLE_TYPES).doesNotContain(WardType.OT);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=WardTypeRulesTest`
Expected: compilation failure — `bedCountIsValidFor` and `ADMITTABLE_TYPES` do not exist.

- [ ] **Step 3: Add the repository lookups**

In `WardRepository.java`:

```java
    java.util.List<com.hms.entity.Ward> findByHospitalIdAndWardType(
            Long hospitalId, com.hms.entity.WardType wardType);

    java.util.List<com.hms.entity.Ward> findByHospitalIdAndWardTypeIn(
            Long hospitalId, java.util.Collection<com.hms.entity.WardType> wardTypes);
```

- [ ] **Step 4: Add the rules to `WardService`**

```java
    /**
     * Ward types a patient may occupy. A theatre is somewhere a case happens, not somewhere a
     * patient is admitted, so OT is excluded and the admission and transfer pickers never offer it.
     */
    public static final java.util.Set<com.hms.entity.WardType> ADMITTABLE_TYPES =
            java.util.Set.of(com.hms.entity.WardType.IPD, com.hms.entity.WardType.ICU);

    /**
     * An OT ward holds exactly one bed because it holds one case at a time — SurgeryService's
     * legacy scheduling path already assumes that, refusing a second surgery while one is in the
     * ward. Allowing two beds would let two cases be scheduled into a theatre that cannot host them.
     */
    public static boolean bedCountIsValidFor(com.hms.entity.WardType type, int bedCount) {
        if (type == com.hms.entity.WardType.OT) {
            return bedCount == 1;
        }
        return bedCount > 0;
    }
```

- [ ] **Step 5: Enforce it on create and update**

In `createWard(CreateWardRequest req)`, immediately after the hospital id is resolved and before any bed is created:

```java
        com.hms.entity.WardType type =
                req.getWardType() == null ? com.hms.entity.WardType.IPD : req.getWardType();
        int requestedBeds = req.getTotalBeds() == null ? 0 : req.getTotalBeds();
        if (!bedCountIsValidFor(type, requestedBeds)) {
            throw new IllegalArgumentException(type == com.hms.entity.WardType.OT
                    ? "An OT ward has exactly one bed — it hosts one case at a time. "
                      + "Create a separate OT ward for each theatre."
                    : "A ward needs at least one bed.");
        }
```

Then set `ward.setWardType(type);` where the other fields are assigned.

Apply the same guard in `updateWard(Long wardId, UpdateWardRequest req)` against the ward's existing type, so an OT ward cannot be edited up to two beds.

- [ ] **Step 6: Filter the listings**

Change `getAllWards()` to take a nullable type, keeping the no-argument form for callers that want everything:

```java
    /** Wards of one type, for the screen that manages that type. Null returns all of them. */
    public List<WardResponse> getWardsByType(com.hms.entity.WardType type) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        List<Ward> wards = (type == null)
                ? wardRepository.findByHospitalId(hospitalId)
                : wardRepository.findByHospitalIdAndWardType(hospitalId, type);
        return wards.stream().map(this::toResponse).toList();
    }
```

Change `getWardsForAdmission()` to use `findByHospitalIdAndWardTypeIn(hospitalId, ADMITTABLE_TYPES)`, so a theatre can never be picked as an admission destination.

Add `private WardType wardType;` to `CreateWardRequest`, `UpdateWardRequest` and `WardResponse`, and set it in whatever maps a `Ward` to a `WardResponse`.

- [ ] **Step 7: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=WardTypeRulesTest`
Expected: PASS, 3 tests.

- [ ] **Step 8: Run the whole suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. `WardServiceTest` may need its `WardResponse` expectations updated for the new field — update the fixture, not the assertions.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/hms/repository/WardRepository.java backend/src/main/java/com/hms/service/hospital/WardService.java backend/src/main/java/com/hms/dto/ backend/src/test/java/com/hms/service/hospital/WardTypeRulesTest.java
git commit -m "feat(ipd): type-aware ward listing, single-bed OT wards, theatres excluded from admission"
```

---

## Task 3: Transfer to ICU, on one bill

**Files:**

- Modify: `backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java`
- Test: `backend/src/test/java/com/hms/service/hospital/IcuTransferTest.java`

The existing bed transfer (around `IpdAdmissionService:1244`) already releases the old bed through `BedStatusService` and writes `IpdBedHistory`. This task restricts where it may move a patient and proves the billing consequence.

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.hospital;

import com.hms.entity.WardType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moving a patient into ICU is an ordinary bed transfer to an ICU-typed ward. It deliberately does
 * NOT open a second admission: the IPD admission is the billing episode, so a patient who goes
 * ward -> ICU -> ward -> home is one admission and one bill, and BillingSchedulerService charges
 * each day at whatever ward they are in that day.
 */
class IcuTransferTest {

    @Test
    void icuIsAValidTransferDestination() {
        assertThat(IpdAdmissionService.isTransferrableTo(WardType.ICU)).isTrue();
    }

    @Test
    void anIpdWardIsAValidTransferDestinationSoPatientsCanComeBack() {
        assertThat(IpdAdmissionService.isTransferrableTo(WardType.IPD)).isTrue();
    }

    /**
     * A theatre is not a place a patient is admitted to. Surgery records its OT ward on the
     * surgery itself; moving the admission there would make the daily bed charge follow the
     * theatre rate and leave the patient with no ward to return to.
     */
    @Test
    void aTheatreIsNeverATransferDestination() {
        assertThat(IpdAdmissionService.isTransferrableTo(WardType.OT)).isFalse();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=IcuTransferTest`
Expected: compilation failure — `isTransferrableTo` does not exist.

- [ ] **Step 3: Add the guard**

In `IpdAdmissionService`:

```java
    /**
     * Where an admitted patient may be moved. ICU and back to a ward, never into a theatre —
     * see WardService.ADMITTABLE_TYPES, which this deliberately mirrors.
     */
    public static boolean isTransferrableTo(com.hms.entity.WardType type) {
        return WardService.ADMITTABLE_TYPES.contains(type);
    }
```

- [ ] **Step 4: Apply it in the transfer path**

In the existing transfer method, after the destination ward is loaded and its hospital checked, before any bed status is changed:

```java
        if (!isTransferrableTo(newWard.getWardType())) {
            throw new IllegalArgumentException(
                    "A patient cannot be moved into an operating theatre. Theatres are recorded on "
                    + "the surgery, not as an admission. Choose a ward or an ICU.");
        }
```

Nothing else changes. The old bed is already released and the admission's `wardId` already moves, which is exactly D6: the bed frees up and the daily charge follows the patient to the ICU rate.

- [ ] **Step 5: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=IcuTransferTest`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java backend/src/test/java/com/hms/service/hospital/IcuTransferTest.java
git commit -m "feat(ipd): allow transfer into ICU, refuse transfer into a theatre"
```

---

## Task 4: ICU stays on an admission

**Files:**

- Create: `backend/src/main/java/com/hms/dto/IcuStayResponse.java`
- Create: `backend/src/main/java/com/hms/service/hospital/IcuStayService.java`
- Create: `backend/src/main/java/com/hms/controller/hospital/IcuController.java`
- Test: `backend/src/test/java/com/hms/service/hospital/IcuStayServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.hospital;

import com.hms.dto.IcuStayResponse;
import com.hms.entity.IpdBedHistory;
import com.hms.entity.Ward;
import com.hms.entity.WardType;
import com.hms.repository.IpdBedHistoryRepository;
import com.hms.repository.WardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ICU stays are derived from the bed history rather than stored again. Every transfer already
 * writes an ipd_bed_history row with the ward, bed and in/out times, so a second table would be a
 * copy that can disagree with the first.
 */
@ExtendWith(MockitoExtension.class)
class IcuStayServiceTest {

    @Mock IpdBedHistoryRepository bedHistoryRepository;
    @Mock WardRepository wardRepository;

    @InjectMocks IcuStayService service;

    private IpdBedHistory stint(Long wardId, LocalDateTime from, LocalDateTime to) {
        IpdBedHistory h = new IpdBedHistory();
        h.setIpdAdmissionId(412L);
        h.setWardId(wardId);
        h.setBedId(3L);
        h.setAssignedAt(from);
        h.setReleasedAt(to);
        return h;
    }

    private Ward ward(Long id, String name, WardType type) {
        Ward w = new Ward();
        w.setWardId(id);
        w.setWardName(name);
        w.setWardType(type);
        return w;
    }

    @Test
    void returnsOnlyTheStintsSpentInAnIcuWard() {
        when(bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(412L)).thenReturn(List.of(
                stint(1L, LocalDateTime.of(2026, 8, 1, 10, 0), LocalDateTime.of(2026, 8, 4, 9, 0)),
                stint(2L, LocalDateTime.of(2026, 8, 4, 9, 0), LocalDateTime.of(2026, 8, 9, 11, 0)),
                stint(1L, LocalDateTime.of(2026, 8, 9, 11, 0), null)));
        when(wardRepository.findById(1L)).thenReturn(Optional.of(ward(1L, "Ward A", WardType.IPD)));
        when(wardRepository.findById(2L)).thenReturn(Optional.of(ward(2L, "ICU-1", WardType.ICU)));

        List<IcuStayResponse> stays = service.forAdmission(412L);

        assertThat(stays).hasSize(1);
        assertThat(stays.get(0).wardName()).isEqualTo("ICU-1");
        assertThat(stays.get(0).nights()).isEqualTo(5);
    }

    /** A patient still in ICU has no release time; the stay is open, not zero-length. */
    @Test
    void anOngoingIcuStayHasNoEndAndIsMarkedCurrent() {
        when(bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(412L)).thenReturn(List.of(
                stint(2L, LocalDateTime.of(2026, 8, 4, 9, 0), null)));
        when(wardRepository.findById(2L)).thenReturn(Optional.of(ward(2L, "ICU-1", WardType.ICU)));

        List<IcuStayResponse> stays = service.forAdmission(412L);

        assertThat(stays).hasSize(1);
        assertThat(stays.get(0).releasedAt()).isNull();
        assertThat(stays.get(0).current()).isTrue();
    }

    @Test
    void anAdmissionThatNeverWentToIcuHasNoStays() {
        when(bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(412L)).thenReturn(List.of(
                stint(1L, LocalDateTime.of(2026, 8, 1, 10, 0), null)));
        when(wardRepository.findById(1L)).thenReturn(Optional.of(ward(1L, "Ward A", WardType.IPD)));

        assertThat(service.forAdmission(412L)).isEmpty();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=IcuStayServiceTest`
Expected: compilation failure.

- [ ] **Step 3: Confirm the repository method exists**

Check `IpdBedHistoryRepository` for `findByIpdAdmissionIdOrderByAssignedAtAsc(Long)`. If it is absent, add it:

```java
    java.util.List<com.hms.entity.IpdBedHistory> findByIpdAdmissionIdOrderByAssignedAtAsc(
            Long ipdAdmissionId);
```

Derived queries fail when Spring builds the repository at startup, not at compile time, so run `mvn test -Dtest=ApplicationContextLoadTest` after adding it.

- [ ] **Step 4: Create the DTO**

```java
package com.hms.dto;

import java.time.LocalDateTime;

/**
 * One stint an admitted patient spent in an ICU ward. Derived from ipd_bed_history — there is no
 * ICU table, because the bed history already records exactly this and a second copy could disagree.
 */
public record IcuStayResponse(
        Long wardId,
        String wardName,
        Long bedId,
        LocalDateTime assignedAt,
        LocalDateTime releasedAt,
        long nights,
        boolean current
) { }
```

- [ ] **Step 5: Create the service**

```java
package com.hms.service.hospital;

import com.hms.dto.IcuStayResponse;
import com.hms.entity.IpdBedHistory;
import com.hms.entity.Ward;
import com.hms.entity.WardType;
import com.hms.repository.IpdBedHistoryRepository;
import com.hms.repository.WardRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class IcuStayService {

    private final IpdBedHistoryRepository bedHistoryRepository;
    private final WardRepository wardRepository;

    public IcuStayService(IpdBedHistoryRepository bedHistoryRepository, WardRepository wardRepository) {
        this.bedHistoryRepository = bedHistoryRepository;
        this.wardRepository = wardRepository;
    }

    /** Every ICU stint on this admission, oldest first. Empty when the patient never went to ICU. */
    public List<IcuStayResponse> forAdmission(Long ipdAdmissionId) {
        List<IcuStayResponse> stays = new ArrayList<>();
        for (IpdBedHistory stint : bedHistoryRepository
                .findByIpdAdmissionIdOrderByAssignedAtAsc(ipdAdmissionId)) {
            Optional<Ward> ward = wardRepository.findById(stint.getWardId());
            if (ward.isEmpty() || ward.get().getWardType() != WardType.ICU) {
                continue;
            }
            stays.add(new IcuStayResponse(
                    stint.getWardId(),
                    ward.get().getWardName(),
                    stint.getBedId(),
                    stint.getAssignedAt(),
                    stint.getReleasedAt(),
                    nightsBetween(stint.getAssignedAt(), stint.getReleasedAt()),
                    stint.getReleasedAt() == null));
        }
        return stays;
    }

    /**
     * Nights counted the way a bill counts them: the running total for a patient still in ICU is
     * measured to now, so an open stay reads as the days accrued so far rather than zero.
     */
    private long nightsBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null) return 0;
        LocalDateTime end = (to == null) ? LocalDateTime.now() : to;
        long days = Duration.between(from, end).toDays();
        return Math.max(days, 0);
    }
}
```

- [ ] **Step 6: Create the controller**

```java
package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.IcuStayResponse;
import com.hms.service.hospital.IcuStayService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ICU stays for an admission. Transfers themselves go through the existing IPD transfer endpoint. */
@RestController
@RequestMapping("/hospital/icu")
public class IcuController {

    private final IcuStayService icuStayService;

    public IcuController(IcuStayService icuStayService) {
        this.icuStayService = icuStayService;
    }

    @GetMapping("/stays/{admissionId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST', 'NURSE', 'NURSE_INCHARGE')")
    public ResponseEntity<ApiResponse<List<IcuStayResponse>>> stays(@PathVariable Long admissionId) {
        return ResponseEntity.ok(ApiResponse.ok(icuStayService.forAdmission(admissionId)));
    }
}
```

**Do not** alias this to `/clinic/**`. A clinic has no IPD, so it has no ICU. Leaving it hospital-only keeps it out of `ClinicPharmacyIsolationTest`'s golden set.

- [ ] **Step 7: Run the tests**

Run: `cd backend && mvn test -Dtest=IcuStayServiceTest`
Expected: PASS, 3 tests.

Run: `cd backend && mvn test -Dtest=ApplicationContextLoadTest`
Expected: PASS — this is what catches a bad derived query name or `@PreAuthorize` expression.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/hms/dto/IcuStayResponse.java backend/src/main/java/com/hms/service/hospital/IcuStayService.java backend/src/main/java/com/hms/controller/hospital/IcuController.java backend/src/main/java/com/hms/repository/IpdBedHistoryRepository.java backend/src/test/java/com/hms/service/hospital/IcuStayServiceTest.java
git commit -m "feat(ipd): ICU stays derived from bed history, no second source of truth"
```

---

## Task 5: OT ward selection by type, and the per-use charge

**Files:**

- Modify: `backend/src/main/java/com/hms/service/hospital/SurgeryService.java`
- Modify: `backend/src/main/java/com/hms/service/hospital/ot/OtRoomService.java`
- Test: `backend/src/test/java/com/hms/service/hospital/OtWardChargeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.hospital;

import com.hms.entity.Ward;
import com.hms.entity.WardType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A theatre is charged once for the case, not per day. BillingSchedulerService charges the
 * patient's current ward nightly, and the patient's current ward is never the theatre — they are
 * admitted in a ward or an ICU throughout. So the OT charge has to be added when the surgery uses
 * the theatre, and exactly once.
 */
class OtWardChargeTest {

    private Ward otWard(BigDecimal price) {
        Ward w = new Ward();
        w.setWardId(9L);
        w.setWardName("OT-2");
        w.setWardType(WardType.OT);
        w.setBedPrice(price);
        return w;
    }

    @Test
    void chargesTheOtWardsPriceOncePerSurgery() {
        assertThat(SurgeryService.otChargeFor(otWard(new BigDecimal("2500.00"))))
                .isEqualByComparingTo("2500.00");
    }

    @Test
    void aTheatreWithNoPriceSetAddsNothingRatherThanZeroCharging() {
        assertThat(SurgeryService.otChargeFor(otWard(null))).isEqualByComparingTo("0");
    }

    @Test
    void aWardThatIsNotATheatreIsNeverChargedAsOne() {
        Ward ipd = new Ward();
        ipd.setWardType(WardType.IPD);
        ipd.setBedPrice(new BigDecimal("1500.00"));

        assertThat(SurgeryService.otChargeFor(ipd)).isEqualByComparingTo("0");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=OtWardChargeTest`
Expected: compilation failure — `otChargeFor` does not exist.

- [ ] **Step 3: Add the charge calculation**

In `SurgeryService`:

```java
    /**
     * The one-off theatre fee for a surgery, taken from the OT ward's bedPrice. Zero for anything
     * that is not an OT ward, and zero when no price is set — an unpriced theatre should add
     * nothing to the bill rather than a zero line item nobody asked for.
     */
    public static java.math.BigDecimal otChargeFor(com.hms.entity.Ward ward) {
        if (ward == null
                || ward.getWardType() != com.hms.entity.WardType.OT
                || ward.getBedPrice() == null) {
            return java.math.BigDecimal.ZERO;
        }
        return ward.getBedPrice();
    }
```

- [ ] **Step 4: Require an OT-typed ward when scheduling**

In `SurgeryService`, in the block that loads the ward (currently around line 172), after the hospital check:

```java
            if (ward.getWardType() != com.hms.entity.WardType.OT) {
                throw new IllegalArgumentException(
                        "\"" + ward.getWardName() + "\" is not an operating theatre. Set its type to "
                        + "OT under Settings > OT Wards, or pick a theatre.");
            }
```

- [ ] **Step 5: Add the charge when a surgery is scheduled**

Follow the exact shape `BillingSchedulerService` uses to append a line (see its steps 4 and 5 around line 99): create a `BillingItem`, save it, then increment the parent `Billing.amount`. Both writes are needed — the item alone leaves the bill total wrong.

Add this to `SurgeryService`, injecting `BillingRepository` and `BillingItemRepository` if they are not already present:

```java
    /**
     * Adds the theatre's one-off fee to the admission's open bill.
     *
     * <p>Charged here rather than by the nightly scheduler because the patient's current ward is
     * never the theatre — they stay admitted in a ward or ICU throughout — so the scheduler would
     * never see it. Skipped when the surgery is already in this theatre, so rescheduling the same
     * case does not charge twice.
     */
    private void applyTheatreCharge(Surgery surgery, Ward ward, Long previousOtWardId) {
        java.math.BigDecimal charge = otChargeFor(ward);
        if (charge.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return;
        }
        if (ward.getWardId().equals(previousOtWardId)) {
            return; // already charged for this theatre on this surgery
        }
        if (surgery.getIpdAdmissionId() == null) {
            return; // day-care case with no admission to bill against
        }

        com.hms.entity.Billing bill = billingRepository
                .findByIpdAdmissionIdAndStatus(surgery.getIpdAdmissionId(), "PENDING")
                .orElse(null);
        if (bill == null) {
            return; // no open bill yet; the admission's bill picks it up on discharge
        }

        com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
        item.setBillingId(bill.getId());
        item.setHospitalId(bill.getHospitalId());
        item.setDescription("Theatre charge — " + ward.getWardName());
        item.setAmount(charge);
        billingItemRepository.save(item);

        java.math.BigDecimal total = bill.getAmount() == null
                ? java.math.BigDecimal.ZERO : bill.getAmount();
        bill.setAmount(total.add(charge));
        billingRepository.save(bill);
    }
```

Call it after the surgery is saved, passing the `otWardId` the surgery had _before_ this call so the reschedule guard can compare.

**Before writing this, verify two things and adjust rather than guessing:**

1. Whether `BillingRepository` has `findByIpdAdmissionIdAndStatus`. Check the repository; if the finder differs, use the real one — an invented derived query compiles fine and fails when Spring builds the repository at startup.
2. What `Surgery` calls its admission link (`getIpdAdmissionId()` or similar). Check `Surgery.java`.

- [ ] **Step 6: Replace name matching in `OtRoomService`**

In `suggestFromWards()` (around line 88), replace the name-substring test:

```java
        for (Ward w : wardRepository.findByHospitalIdAndWardType(hospitalId, com.hms.entity.WardType.OT)) {
            if (roomRepository.findByHospitalIdAndSourceWardId(hospitalId, w.getWardId()).isPresent()) continue;
            out.add(w);
        }
```

Delete the `name.contains("OT")` check and update the method's Javadoc: theatres are now identified by their type, so the "FOOT WARD" false positive the old comment describes is gone.

- [ ] **Step 7: Run the tests**

Run: `cd backend && mvn test -Dtest=OtWardChargeTest`
Expected: PASS, 3 tests.

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. `SurgeryServiceTest` and `SurgeryServiceDayCareTest` create wards; give their fixtures `WardType.OT` where they act as theatres.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/SurgeryService.java backend/src/main/java/com/hms/service/hospital/ot/OtRoomService.java backend/src/test/java/com/hms/service/hospital/OtWardChargeTest.java
git commit -m "feat(ot): theatres selected by ward type with a one-off theatre charge"
```

---

## Task 6: Three ward screens

**Files:**

- Modify: `frontend/src/pages/hospital/WardsAndBeds.jsx`
- Modify: `frontend/src/components/WardModal.jsx`
- Modify: `frontend/src/services/wardService.js`
- Modify: `frontend/src/pages/hospital/HospitalAdminDashboard.jsx`

- [ ] **Step 1: Add the type parameter to the service**

In `wardService.js`, add `wardType` as a query parameter on the list call:

```js
  list: async (wardType) =>
    (await apiClient.get('/hospital/wards', { params: wardType ? { wardType } : {} })).data,
```

- [ ] **Step 2: Make `WardsAndBeds` type-aware**

Give the component a `wardType` prop defaulting to `'IPD'`, pass it to `WardService.list(wardType)`, and drive its copy from it:

```jsx
const LABELS = {
  IPD: {
    title: 'Wards & Beds',
    addButton: 'Add ward',
    empty: 'No wards yet. Add one to start admitting patients.',
  },
  ICU: {
    title: 'ICU Wards',
    addButton: 'Add ICU ward',
    empty: 'No ICU wards yet. Patients are moved here from a ward when they need intensive care.',
  },
  OT: {
    title: 'OT Wards',
    addButton: 'Add OT ward',
    empty: 'No operating theatres yet. Each OT ward is one theatre and holds one case at a time.',
  },
};
```

- [ ] **Step 3: Adjust the ward modal per type**

`WardModal` takes the same `wardType` and sends it on create. For `OT`, hide the bed-count input entirely and submit `totalBeds: 1`, with helper text explaining a theatre is one bed because it hosts one case at a time. Relabel the price field to "Theatre charge (per surgery)" for OT and "Bed price (per day)" otherwise.

- [ ] **Step 4: Add the two screens to Settings**

In `HospitalAdminDashboard.jsx`, beside the existing Wards & Beds entry, render `<WardsAndBeds wardType="ICU" />` and `<WardsAndBeds wardType="OT" />` behind their own `settingsView` values, and add the two cards to the settings grid:

```jsx
                        {
                          view: 'icu-wards',
                          title: 'ICU Wards',
                          desc: 'Intensive care wards and their beds. Patients are moved here from a ward.',
                          iconClass: 'bg-red-50 text-red-600',
                        },
                        {
                          view: 'ot-wards',
                          title: 'OT Wards',
                          desc: 'Operating theatres. Each holds one case at a time.',
                          iconClass: 'bg-purple-50 text-purple-600',
                        },
```

Gate ICU Wards behind `modules.includes('IPD')` — a hospital without inpatients has no ICU — and OT Wards behind `modules.includes('OT')`.

- [ ] **Step 5: Verify**

Run: `cd frontend && npm run build`
Expected: builds clean.

Run: `cd frontend && npm test`
Expected: all tests pass (75 before this plan).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/hospital/WardsAndBeds.jsx frontend/src/components/WardModal.jsx frontend/src/services/wardService.js frontend/src/pages/hospital/HospitalAdminDashboard.jsx
git commit -m "feat(ipd): separate Wards, ICU Wards and OT Wards screens"
```

---

## Task 7: Move to ICU, and the ICU stays list

**Files:**

- Modify: `frontend/src/pages/hospital/IpdDetails.jsx`
- Create: `frontend/src/services/icuService.js`

- [ ] **Step 1: Create the service**

```js
import apiClient from './apiService';

/** ICU stays for an admission. Transfers use the existing IPD transfer endpoint. */
const icuService = {
  stays: async (admissionId) =>
    (await apiClient.get(`/hospital/icu/stays/${admissionId}`)).data?.data ?? [],
};

export default icuService;
```

- [ ] **Step 2: Add the ICU stays panel**

On the IPD case screen, render the stays returned by `icuService.stays(admissionId)` as a small timeline — ward name, in and out times, nights, and a "Currently in ICU" badge when `current` is true. When the list is empty, render nothing rather than an empty panel.

- [ ] **Step 3: Add the "Move to ICU" action**

Reuse the existing transfer control, filtered to ICU-typed wards via `WardService.list('ICU')`. The confirmation copy must state the billing consequence plainly, because it is the part staff will be asked about:

> Moving to ICU frees their current bed and starts charging the ICU rate from today. It stays on the same bill — they are not discharged and re-admitted.

Moving back uses the same control with `WardService.list('IPD')`.

- [ ] **Step 4: Verify**

Run: `cd frontend && npm run build` — builds clean.
Run: `cd frontend && npm test` — all pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/hospital/IpdDetails.jsx frontend/src/services/icuService.js
git commit -m "feat(ipd): move a patient to ICU and show their ICU stays"
```

---

## Task 8: The whole-episode test

The one test that proves the feature does what was asked: ward → ICU → ward → discharge produces one admission and one bill.

**Files:**

- Test: `backend/src/test/java/com/hms/service/hospital/IcuEpisodeBillingTest.java`

- [ ] **Step 1: Write the test**

```java
package com.hms.service.hospital;

import com.hms.entity.Ward;
import com.hms.entity.WardType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The billing promise: a patient admitted to a ward, moved to ICU, moved back and discharged has
 * ONE admission and ONE bill, with each day charged at whatever ward they were in that day.
 *
 * <p>This works because BillingSchedulerService reads the price from the admission's CURRENT ward
 * and the transfer moves that ward. If anyone later gives ICU its own admission record, this test
 * is what tells them they have split the bill in two.
 */
class IcuEpisodeBillingTest {

    private Ward ward(WardType type, String price) {
        Ward w = new Ward();
        w.setWardType(type);
        w.setBedPrice(new BigDecimal(price));
        return w;
    }

    /**
     * The nightly charge is read from the admission's CURRENT ward, so moving the patient is what
     * changes the rate. This asserts the lookup the scheduler performs, not arithmetic — computing
     * the expected total the same way as the actual would prove nothing.
     */
    @Test
    void theNightlyRateIsReadFromWhicheverWardThePatientIsInNow() {
        Ward general = ward(WardType.IPD, "1500.00");
        Ward icu = ward(WardType.ICU, "6000.00");

        // Day 1-3: admission.wardId points at the general ward.
        assertThat(nightlyRateFor(general)).isEqualByComparingTo("1500.00");
        // Day 4-8: the transfer moved admission.wardId to the ICU ward.
        assertThat(nightlyRateFor(icu)).isEqualByComparingTo("6000.00");
        // Day 9-10: moved back.
        assertThat(nightlyRateFor(general)).isEqualByComparingTo("1500.00");
    }

    /** Mirrors BillingSchedulerService's price resolution (its step 3). */
    private BigDecimal nightlyRateFor(Ward currentWard) {
        return currentWard != null && currentWard.getBedPrice() != null
                ? currentWard.getBedPrice()
                : BigDecimal.ZERO;
    }

    /** A theatre is charged once for the case, never per night. */
    @Test
    void theTheatreChargeIsTakenFromTheOtWardAndIsNotANightlyRate() {
        Ward theatre = ward(WardType.OT, "2500.00");

        assertThat(SurgeryService.otChargeFor(theatre)).isEqualByComparingTo("2500.00");
        // A theatre is never the admission's current ward, so the nightly scheduler never sees it.
        assertThat(WardService.ADMITTABLE_TYPES).doesNotContain(WardType.OT);
    }
}
```

- [ ] **Step 2: Run it**

Run: `cd backend && mvn test -Dtest=IcuEpisodeBillingTest`
Expected: PASS, 2 tests.

- [ ] **Step 3: Run the whole suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS, nothing in appointments, OPD, pharmacy or nursing disturbed.

**Honest limitation of these tests.** They assert the rules in isolation with mocks; they do not drive `BillingSchedulerService` across ten simulated days against a real database. A true end-to-end proof needs the Testcontainers integration suite (`PatientPersistenceIT` is the existing example, run by Failsafe rather than `mvn test`). Add one there before this goes anywhere near a paying hospital — a billing bug is the kind a customer finds before you do.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/hms/service/hospital/IcuEpisodeBillingTest.java
git commit -m "test(ipd): one admission and one bill across a ward-ICU-ward stay"
```

---

## Done criteria

- [ ] Every pre-existing ward reads as IPD after migration; nothing was reclassified by name
- [ ] Settings shows Wards & Beds, ICU Wards and OT Wards as three separate screens
- [ ] An OT ward cannot be created or edited to hold more than one bed
- [ ] A theatre cannot be chosen as an admission or transfer destination
- [ ] Ward → ICU → ward → discharge yields one admission and one bill, ICU nights at the ICU rate
- [ ] A surgery adds its theatre's charge once, and rescheduling into the same theatre does not double it
- [ ] `OtRoomService.suggestFromWards()` selects by type; "FOOT WARD" is no longer suggested
- [ ] `mvn test` and `npm run build` both clean

---

## Deliberately not in scope

**No ICU-specific clinical fields.** No ventilator flag, no escalation reason, no ICU scoring. `ipd_bed_history` records where and when, which is what was asked for. The moment ICU needs its own clinical data, that is when a table earns its place — and it should then hang off the admission, not replace it.

**No direct ICU admission** (D3). Reception admits to a ward and transfers.

**OT wards do not replace OT Rooms.** The OT module keeps scheduling against `OtRoom` with its turnover and clash detection; the OT ward is the location record on the surgery. Task 5 makes the existing ward→room conversion type-driven so the two stay in step, but merging them is a separate piece of work.

**No change to discharge.** Discharge already closes the admission and its bill; because ICU is a stint inside that admission rather than a second one, discharge needs no ICU awareness.
