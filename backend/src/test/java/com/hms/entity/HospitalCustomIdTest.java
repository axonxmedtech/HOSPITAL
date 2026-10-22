package com.hms.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HospitalCustomIdTest {
    @Test
    void allocationIsNotLimitedToNineThousandIdentifiers() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 9001; i++) {
            Hospital hospital = new Hospital();
            hospital.setType(HospitalType.HOSPITAL);
            hospital.generateIds();
            assertThat(ids.add(hospital.getCustomId())).as("new tenant identifier %s", i).isTrue();
        }
    }

    @Test
    void assignedIdentifiersAreNeverReplaced() {
        Hospital hospital = new Hospital();
        hospital.setCustomId("HSP1234");
        hospital.setPublicId("existing-public-identifier");
        hospital.generateIds();
        assertThat(hospital.getCustomId()).isEqualTo("HSP1234");
        assertThat(hospital.getPublicId()).isEqualTo("existing-public-identifier");
    }

    @Test
    void tenantTypePrefixesArePreserved() {
        for (HospitalType type : HospitalType.values()) {
            Hospital hospital = new Hospital();
            hospital.setType(type);
            hospital.generateIds();
            String prefix = switch (type) {
                case HOSPITAL -> "HSP";
                case CLINIC -> "CLN";
                case PHARMACY -> "PHR";
            };
            assertThat(hospital.getCustomId()).startsWith(prefix);
        }
    }
}
