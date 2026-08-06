package com.hms.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private PatientRequest valid() {
        PatientRequest r = new PatientRequest();
        r.setName("Ramesh Patel");
        r.setGender("Male");
        r.setPhone("9876543210");
        return r;
    }

    @Test
    void acceptsAValidManualPatient() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void rejectsBlankPhoneForManualEntry() {
        PatientRequest r = valid();
        r.setPhone("");
        assertThat(validator.validate(r)).isNotEmpty();
    }

    @Test
    void rejectsNonTenDigitPhone() {
        PatientRequest r = valid();
        r.setPhone("+919876543210");
        assertThat(validator.validate(r)).isNotEmpty();
    }

    @Test
    void rejectsBlankGender() {
        PatientRequest r = valid();
        r.setGender("");
        assertThat(validator.validate(r)).isNotEmpty();
    }
}
