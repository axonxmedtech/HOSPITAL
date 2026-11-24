package com.hms.service.hospital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Every path that writes a bill's payment method goes through one allow-list. */
class BillingPaymentModeTest {

    @Test
    void acceptsTheThreeModesInAnyCase() {
        assertEquals("CASH", BillingService.normaliseMode("cash"));
        assertEquals("CARD", BillingService.normaliseMode(" Card "));
        assertEquals("UPI", BillingService.normaliseMode("UPI"));
    }

    @Test
    void readsTheOldOnlineLabelAsUpi() {
        assertEquals("UPI", BillingService.normaliseMode("ONLINE"));
    }

    @Test
    void refusesAnythingElse() {
        assertThrows(IllegalArgumentException.class, () -> BillingService.normaliseMode("MONOPOLY MONEY"));
        assertThrows(IllegalArgumentException.class, () -> BillingService.normaliseMode(""));
        assertThrows(IllegalArgumentException.class, () -> BillingService.normaliseMode(null));
    }
}
