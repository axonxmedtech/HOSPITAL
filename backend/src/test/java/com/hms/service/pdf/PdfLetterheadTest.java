package com.hms.service.pdf;

import com.hms.entity.Hospital;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Letterhead text must come from the tenant itself. A missing address once fell back to a real
 * clinic's address, and doctor names already titled "Dr" printed as "Dr. DR ...".
 */
class PdfLetterheadTest {

    @Test
    void missingAddressPrintsNothingRatherThanAnotherHospitalsAddress() {
        assertThat(PdfLayoutHelper.hospitalAddress(null)).isEmpty();
        assertThat(PdfLayoutHelper.hospitalAddress(new Hospital())).isEmpty();
    }

    @Test
    void hospitalsOwnAddressIsUsed() {
        Hospital h = new Hospital();
        h.setAddress("  12 MG Road, Mumbai ");
        assertThat(PdfLayoutHelper.hospitalAddress(h)).isEqualTo("12 MG Road, Mumbai");
    }

    @Test
    void doctorTitleIsNotDoubled() {
        assertThat(PdfLayoutHelper.doctorTitle("Asha Kulkarni")).isEqualTo("Dr. ASHA KULKARNI");
        assertThat(PdfLayoutHelper.doctorTitle("Dr Asha Kulkarni")).isEqualTo("Dr. ASHA KULKARNI");
        assertThat(PdfLayoutHelper.doctorTitle("dr. asha")).isEqualTo("Dr. ASHA");
        assertThat(PdfLayoutHelper.doctorTitle("DR.  Asha")).isEqualTo("Dr. ASHA");
    }

    @Test
    void namesThatMerelyStartWithDrAreKept() {
        assertThat(PdfLayoutHelper.doctorTitle("Drishti Rao")).isEqualTo("Dr. DRISHTI RAO");
    }

    @Test
    void missingNameFallsBackToGenericTitle() {
        assertThat(PdfLayoutHelper.doctorTitle(null)).isEqualTo("Dr. DOCTOR");
        assertThat(PdfLayoutHelper.doctorTitle("  ")).isEqualTo("Dr. DOCTOR");
    }
}
