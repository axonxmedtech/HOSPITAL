package com.hms.integration;

import com.hms.service.pharmacy.PharmacyInvoiceNumberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pharmacy invoice numbers on real MySQL. H2 hid a deadlock here: a locking read of a series row
 * that did not exist yet locked the gap it belongs in, and the insert creating it then waited on
 * the same transaction until the lock timeout, so the first bill of every series failed.
 */
class PharmacyInvoiceNumberIT extends AbstractMySqlIT {

    @Autowired PharmacyInvoiceNumberService numbers;
    @Autowired TransactionTemplate tx;

    private String next(long hospitalId, Long branchId) {
        return tx.execute(status -> numbers.next(hospitalId, branchId));
    }

    @Test
    void theFirstBillOfANewSeriesGetsNumberOneAndTheNextIsTwo() {
        String first = next(9_001L, null);
        String second = next(9_001L, null);

        assertThat(first).matches("PH/\\d{2}-\\d{2}/00001");
        assertThat(second).matches("PH/\\d{2}-\\d{2}/00002");
        assertThat(next(9_001L, 3L)).matches("B3/\\d{2}-\\d{2}/00001");
    }

    @Test
    void countersBillingAtOnceNeverShareOrSkipANumber() throws Exception {
        int counters = 8;
        long hospitalId = 9_002L; // a brand-new series: the first numbers race to create it
        ExecutorService pool = Executors.newFixedThreadPool(counters);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < counters; i++) {
            Callable<String> bill = () -> {
                start.await();
                return next(hospitalId, null);
            };
            results.add(pool.submit(bill));
        }
        start.countDown();
        Set<Integer> taken = new TreeSet<>();
        for (Future<String> f : results) {
            String n = f.get(60, TimeUnit.SECONDS);
            taken.add(Integer.parseInt(n.substring(n.lastIndexOf('/') + 1)));
        }
        pool.shutdown();

        assertThat(taken).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }
}
