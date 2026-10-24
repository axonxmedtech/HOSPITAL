package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;

import com.hms.service.import_.LegacyPhoneNormalizer.Kind;
import com.hms.service.import_.LegacyPhoneNormalizer.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The frozen algorithm, example by example. All numbers are synthetic. */
class LegacyPhoneNormalizerTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "9876543210        | 9876543210",
                "98765 43210       | 9876543210",
                "98765-43210       | 9876543210",
                "+91 98765 43210   | 9876543210",
                "+919876543210     | 9876543210",
                "919876543210      | 9876543210",
                "09876543210       | 9876543210",
                "(98765) 43210     | 9876543210",
                "98765.43210       | 9876543210",
                "  9876543210\t    | 9876543210",
                "91 9876543210     | 9876543210",
                "0 98765 43210     | 9876543210",
                "00876543210       | 0876543210", // trunk 0 stripped once; the remaining ten digits are what they are
            })
    void deterministicallyCanonical(String raw, String expected) {
        Result r = LegacyPhoneNormalizer.normalize(raw);
        assertThat(r.kind()).isEqualTo(Kind.CANONICAL);
        assertThat(r.canonical()).isEqualTo(expected);
    }

    @Test
    void aGenuineTenDigitNumberStartingWith91IsUntouched() {
        Result r = LegacyPhoneNormalizer.normalize("9123456780");
        assertThat(r.kind()).isEqualTo(Kind.CANONICAL);
        assertThat(r.canonical()).isEqualTo("9123456780");
        assertThat(r.asImportedIfDifferent()).isNull();
    }

    @Test
    void nbspIsAFormattingCharacter() {
        Result r = LegacyPhoneNormalizer.normalize("98765 43210");
        assertThat(r.canonical()).isEqualTo("9876543210");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", " ", "-", "( )", " - "})
    void blankIsBlankNotAnError(String raw) {
        Result r = LegacyPhoneNormalizer.normalize(raw);
        assertThat(r.kind()).isEqualTo(Kind.BLANK);
        assertThat(r.canonical()).isNull();
        assertThat(r.original()).isNull();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is unrecoverable")
    @ValueSource(
            strings = {
                "N/A", "n/a", "none", "1234567", "98765ABC10", "98765432101", "987654321",
                "9876543210 / 9123456780", "9876543210,9123456780", "+1 415 555 0100", "+9198765432100",
                "0098765432100", "919876543210x", "91-98765-4321", "12345678901", "000876543210"
            })
    void anythingElseIsUnrecoverableAndNoDigitIsInvented(String raw) {
        Result r = LegacyPhoneNormalizer.normalize(raw);
        assertThat(r.kind()).isEqualTo(Kind.UNRECOVERABLE);
        assertThat(r.canonical()).isNull();
        assertThat(r.original()).isEqualTo(raw.strip());
    }

    @Test
    void theOriginalIsOfferedForPreservationOnlyWhenItDiffers() {
        assertThat(LegacyPhoneNormalizer.normalize("+91 98765-43210").asImportedIfDifferent()).isEqualTo("+91 98765-43210");
        assertThat(LegacyPhoneNormalizer.normalize(" 09876543210 ").asImportedIfDifferent()).isEqualTo("09876543210");
        assertThat(LegacyPhoneNormalizer.normalize("9876543210").asImportedIfDifferent()).isNull();
        assertThat(LegacyPhoneNormalizer.normalize("N/A").asImportedIfDifferent()).isNull(); // not canonical: nothing to pair it with
    }
}
