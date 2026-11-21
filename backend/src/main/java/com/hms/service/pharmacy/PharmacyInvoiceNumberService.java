package com.hms.service.pharmacy;

import com.hms.entity.pharmacy.PharmacyInvoiceSequence;
import com.hms.repository.pharmacy.PharmacyInvoiceSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Hands out pharmacy invoice numbers: {@code PH/26-27/00001} for a pharmacy,
 * {@code B3/26-27/00001} for branch 3 of a multi-branch pharmacy. Unique within the facility,
 * consecutive within a financial year (April to March), at most 16 characters as GST requires.
 *
 * <p>Taken inside the sale's own transaction: if the sale fails, the number is not used up.
 */
@Service
@RequiredArgsConstructor
public class PharmacyInvoiceNumberService {

    private final PharmacyInvoiceSequenceRepository repository;
    private final PlatformTransactionManager transactionManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Long hospitalId, Long branchId) {
        return nextIn(hospitalId, branchId == null ? "PH" : "B" + branchId);
    }

    /** Supplier return notes: SR/26-27/00001, or SR3/26-27/00001 for branch 3. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextReturnNote(Long hospitalId, Long branchId) {
        return nextIn(hospitalId, branchId == null ? "SR" : "SR" + branchId);
    }

    private String nextIn(Long hospitalId, String prefix) {
        String series = prefix + "/" + financialYear(LocalDate.now(ZoneId.systemDefault()));
        // Create the series row BEFORE taking the lock. A locking read of a row that does not exist
        // yet makes MySQL lock the gap it would go in, and the insert below (in its own
        // transaction) would then wait on this very transaction forever.
        if (!repository.existsByHospitalIdAndSeries(hospitalId, series)) {
            createSeries(hospitalId, series);
        }
        PharmacyInvoiceSequence seq = repository.lockSeries(hospitalId, series)
                .orElseThrow(() -> new IllegalStateException("Invoice series could not be created"));
        seq.setLastNumber(seq.getLastNumber() + 1);
        repository.save(seq);
        return series + "/" + String.format("%05d", seq.getLastNumber());
    }

    /** First bill of a series. Two first bills at once: one insert wins, the other finds it made. */
    private void createSeries(Long hospitalId, String series) {
        TransactionTemplate own = new TransactionTemplate(transactionManager);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            own.executeWithoutResult(status -> {
                PharmacyInvoiceSequence fresh = new PharmacyInvoiceSequence();
                fresh.setHospitalId(hospitalId);
                fresh.setSeries(series);
                fresh.setLastNumber(0L);
                repository.saveAndFlush(fresh);
            });
        } catch (DataIntegrityViolationException alreadyCreated) {
            // another counter created it a moment ago
        }
    }

    /** 21 Nov 2026 → "26-27"; 15 Feb 2027 → "26-27"; 1 Apr 2027 → "27-28". */
    static String financialYear(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return String.format("%02d-%02d", start % 100, (start + 1) % 100);
    }
}
