package com.hms.service.hospital;

import com.hms.dto.BedResponse;
import com.hms.entity.Bed;
import com.hms.repository.BedRepository;
import com.hms.security.SecurityContextHelper;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BedService {

    private final BedRepository bedRepository;
    private final SecurityContextHelper securityHelper;

    public BedService(BedRepository bedRepository, SecurityContextHelper securityHelper) {
        this.bedRepository = bedRepository;
        this.securityHelper = securityHelper;
    }

    @Transactional
    public BedResponse updateStatus(Long bedId, String status) {
        // UI-initiated update (default) - enforce stricter rules.
        return updateStatus(bedId, status, false);
    }

    /**
     * Update bed status. When `systemInitiated` is false (UI request), only allow
     * transition from `maintenance` -> `available`. Other transitions must be
     * performed by system flows (admission/discharge) and should call with
     * `systemInitiated=true` to bypass the UI restriction.
     */
    @Transactional
    public BedResponse updateStatus(Long bedId, String status, boolean systemInitiated) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Bed b = bedRepository.findById(bedId).orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        if (b.getHospitalId() == null || !b.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied");

        b.setStatus(status.toLowerCase());
        Bed saved = bedRepository.save(b);
        BedResponse r = new BedResponse();
        r.setBedId(saved.getBedId());
        r.setBedCode(saved.getBedCode());
        r.setStatus(saved.getStatus());
        return r;
    }

    public List<BedResponse> getAvailableBeds(Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        List<Bed> list;
        if (wardId != null) {
            list = bedRepository.findByWardIdAndHospitalId(wardId, hospitalId);
        } else {
            list = bedRepository.findByHospitalId(hospitalId);
        }
        return list.stream()
                .filter(b -> b.getStatus() != null && b.getStatus().equalsIgnoreCase("available"))
                .map(b -> {
                    BedResponse br = new BedResponse();
                    br.setBedId(b.getBedId());
                    br.setBedCode(b.getBedCode());
                    br.setStatus(b.getStatus());
                    return br;
                }).collect(Collectors.toList());
    }

    private boolean isValidStatus(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        return "available".equals(lower) || "occupied".equals(lower) || "maintenance".equals(lower) || "cleaning".equals(lower);
    }
}

