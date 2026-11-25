package com.hms.service.hospital;

import com.hms.entity.RequestIdempotency;
import com.hms.repository.RequestIdempotencyRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Idempotency for clinical orders (IPD prescriptions, lab tests): a retried request carrying the
 * same key is answered with what the first attempt created, never applied twice.
 *
 * <p>Call {@link #replay} and {@link #record} inside the order's transaction and <b>under a lock
 * that serialises the same act</b> (both callers hold the admission's row lock), so two copies of
 * one request run one after the other and the second finds the first's row. The unique key on
 * (hospital, scope, key) is the backstop.
 */
@Service
public class RequestIdempotencyService {

    public static final String IPD_PRESCRIPTION = "IPD_PRESCRIPTION";
    public static final String IPD_INVESTIGATION = "IPD_INVESTIGATION";

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final RequestIdempotencyRepository repository;

    public RequestIdempotencyService(RequestIdempotencyRepository repository) {
        this.repository = repository;
    }

    /** The key as given, or null when the caller sent none. A malformed key is refused. */
    public static String normalise(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim();
        if (!KEY.matcher(k).matches()) throw new IllegalArgumentException("Invalid idempotency key");
        return k;
    }

    /** The ids an earlier attempt of this act created, if it was already applied. */
    public Optional<List<Long>> replay(Long hospitalId, String scope, String key) {
        if (key == null) return Optional.empty();
        return repository.findByHospitalIdAndScopeAndIdempotencyKey(hospitalId, scope, key)
                .map(r -> Arrays.stream(r.getResultIds().split(","))
                        .filter(s -> !s.isBlank()).map(Long::valueOf).toList());
    }

    /** Remembers what this act created, so a retry is answered from it. */
    public void record(Long hospitalId, String scope, String key, List<Long> resultIds) {
        if (key == null) return;
        RequestIdempotency r = new RequestIdempotency();
        r.setHospitalId(hospitalId);
        r.setScope(scope);
        r.setIdempotencyKey(key);
        r.setResultIds(resultIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        repository.save(r);
    }
}
