package com.hms.service.hospital;

import com.hms.entity.Hospital;
import com.hms.repository.HospitalRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The registration details printed on every pharmacy tax invoice: the facility's GSTIN and drug
 * licence number. Read by any staff role that bills; changed by the facility admin only.
 */
@Service
@RequiredArgsConstructor
public class PharmacyInvoiceDetailsService {

    /** 2-digit state code, 10-character PAN, entity number, "Z", check character. */
    private static final Pattern GSTIN = Pattern.compile("^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final int MAX_LICENSE = 120;

    private final HospitalRepository hospitalRepository;
    private final SecurityContextHelper securityHelper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Map<String, Object> get() {
        return view(load());
    }

    @Transactional
    public Map<String, Object> update(String gstin, String drugLicenseNumber) {
        String g = gstin == null ? "" : gstin.trim().toUpperCase(Locale.ROOT);
        if (!g.isEmpty() && !GSTIN.matcher(g).matches()) {
            throw new IllegalArgumentException("GSTIN must be 15 characters, e.g. 27ABCDE1234F1Z5");
        }
        String dl = drugLicenseNumber == null ? "" : drugLicenseNumber.trim();
        if (dl.length() > MAX_LICENSE) {
            throw new IllegalArgumentException("Drug licence number is too long (max " + MAX_LICENSE + " characters)");
        }
        Hospital h = load();
        String before = "GSTIN " + blankAsDash(h.getGstin()) + ", DL " + blankAsDash(h.getDrugLicenseNumber());
        h.setGstin(g.isEmpty() ? null : g);
        h.setDrugLicenseNumber(dl.isEmpty() ? null : dl);
        hospitalRepository.save(h);
        try {
            auditLogService.logAction("PHARMACY_INVOICE_DETAILS_UPDATED",
                    "Invoice details changed from [" + before + "] to [GSTIN " + blankAsDash(h.getGstin())
                            + ", DL " + blankAsDash(h.getDrugLicenseNumber()) + "]",
                    securityHelper.getCurrentUserEmail(), h.getId(), "HOSPITAL", h.getPublicId(), null);
        } catch (RuntimeException ignored) {
            // audit is best-effort
        }
        return view(h);
    }

    /** The caller's own facility; the id comes from the signed-in session, never from the request. */
    private Hospital load() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found"));
    }

    private static Map<String, Object> view(Hospital h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gstin", h.getGstin());
        m.put("drugLicenseNumber", h.getDrugLicenseNumber());
        m.put("complete", h.getGstin() != null && h.getDrugLicenseNumber() != null);
        return m;
    }

    private static String blankAsDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
