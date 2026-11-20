package com.hms.service.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Client feedback #3: an English + Marathi print is bilingual in every section, the same way. */
class PrintLabelsTest {

    private static final String MR = PatientInstructionFormatter.MODE_EN_MR;
    private static final String EN = PatientInstructionFormatter.MODE_EN;

    @Test
    void headingsAreEnglishSlashMarathi() {
        assertThat(PrintLabels.label("medicine", MR)).isEqualTo("Medicine / औषध");
        assertThat(PrintLabels.label("treatment", MR)).isEqualTo("TREATMENT & CLINICAL NOTES / उपचार");
        assertThat(PrintLabels.label("medicine", EN)).isEqualTo("Medicine");
    }

    @Test
    void doseFrequencyAndDurationCarryTheirMarathi() {
        assertThat(PrintLabels.dose("1/2 tablet", MR)).isEqualTo("1/2 tablet / 1/2 गोळी");
        assertThat(PrintLabels.dose("5 ml", MR)).isEqualTo("5 ml / 5 मिली");
        assertThat(PrintLabels.frequency("1-0-1", MR)).isEqualTo("1-0-1 / सकाळ-रात्र");
        assertThat(PrintLabels.duration("3 Days", MR)).isEqualTo("3 Days / 3 दिवस");
        assertThat(PrintLabels.duration("2 Weeks", MR)).isEqualTo("2 Weeks / 2 आठवडे");
    }

    @Test
    void englishPrintsAndUnrecognisedTextAreUnchanged() {
        assertThat(PrintLabels.dose("1/2 tablet", EN)).isEqualTo("1/2 tablet");
        assertThat(PrintLabels.dose("as directed", MR)).isEqualTo("as directed");
        assertThat(PrintLabels.frequency("SOS", MR)).isEqualTo("SOS");
        assertThat(PrintLabels.duration("Once", MR)).isEqualTo("Once");
    }
}
