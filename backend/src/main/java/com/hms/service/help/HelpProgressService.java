package com.hms.service.help;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.HelpProgress;
import com.hms.repository.HelpProgressRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.UserAuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and saves the signed-in user's own help progress. The row is always found by a key built
 * from the caller's token, never from the request, so nobody can read or change another user's.
 */
@Service
public class HelpProgressService {

    /** A generous ceiling: real progress is a few hundred bytes. */
    static final int MAX_BYTES = 20_000;

    private final HelpProgressRepository repository;
    private final SecurityContextHelper securityHelper;
    private final ObjectMapper mapper;

    public HelpProgressService(HelpProgressRepository repository, SecurityContextHelper securityHelper,
                               ObjectMapper mapper) {
        this.repository = repository;
        this.securityHelper = securityHelper;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public JsonNode read() {
        return repository.findByScopeKey(scopeKey(securityHelper.getCurrentUserDetails()))
                .map(p -> parse(p.getData()))
                .orElseGet(mapper::createObjectNode);
    }

    @Transactional
    public JsonNode save(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("Help progress must be a JSON object");
        }
        String json;
        try {
            json = mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Help progress could not be read");
        }
        if (json.length() > MAX_BYTES) {
            throw new IllegalArgumentException("Help progress is too large");
        }
        UserAuthenticationDetails me = securityHelper.getCurrentUserDetails();
        String key = scopeKey(me);
        HelpProgress row = repository.findByScopeKey(key).orElseGet(() -> {
            HelpProgress p = new HelpProgress();
            p.setScopeKey(key);
            p.setHospitalId(me.getHospitalId());
            return p;
        });
        row.setData(json);
        repository.save(row);
        return data;
    }

    /** role : facility : branch : user. A Super Admin has no facility ("P" for platform). */
    static String scopeKey(UserAuthenticationDetails me) {
        String facility = me.getHospitalId() == null ? "P" : String.valueOf(me.getHospitalId());
        String branch = me.getBranchId() == null ? "-" : String.valueOf(me.getBranchId());
        return me.getRole() + ":" + facility + ":" + branch + ":" + me.getUserId();
    }

    private JsonNode parse(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return node != null && node.isObject() ? node : mapper.createObjectNode();
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }
}
