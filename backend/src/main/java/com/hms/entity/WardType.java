package com.hms.entity;

/**
 * What a ward is for.
 *
 * <p>Beds, pricing and the nurse incharge work identically across all three — there is one
 * implementation to keep correct rather than three. The type decides which screen a ward appears
 * on, where a patient may be admitted or transferred, and whether the OT module treats it as a
 * theatre.
 */
public enum WardType {
    /** Ordinary inpatient ward. Every ward created before typing existed is one of these. */
    IPD,
    /** Intensive care. A patient reaches one by transfer from an IPD ward, never directly. */
    ICU,
    /** Operating theatre. Holds exactly one bed, because it holds one case at a time. */
    OT
}
