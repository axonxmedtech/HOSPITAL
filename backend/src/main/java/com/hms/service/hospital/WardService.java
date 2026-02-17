package com.hms.service.hospital;

import com.hms.dto.*;
import com.hms.entity.Bed;
import com.hms.entity.Ward;
import com.hms.repository.BedRepository;
import com.hms.repository.WardRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WardService {

    private final WardRepository wardRepository;
    private final BedRepository bedRepository;
    private final SecurityContextHelper securityHelper;

    public WardService(WardRepository wardRepository, BedRepository bedRepository, SecurityContextHelper securityHelper) {
        this.wardRepository = wardRepository;
        this.bedRepository = bedRepository;
        this.securityHelper = securityHelper;
    }

    @Transactional
    public WardResponse createWard(CreateWardRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();

        Ward ward = new Ward();
        ward.setHospitalId(hospitalId);
        ward.setWardName(req.getWardName());
        ward.setBedPrice(req.getBedPrice());
        ward.setTotalBeds(req.getTotalBeds());
        ward.setFloorNumber(req.getFloorNumber());

        Ward saved = wardRepository.save(ward);

        // auto-create beds
        int total = req.getTotalBeds() == null ? 0 : req.getTotalBeds();
        // ensure unique bed codes within a ward by checking existing highest index
        int startIndex = 1;
        List<Bed> existing = bedRepository.findByWardIdAndHospitalId(saved.getWardId(), hospitalId);
        if (existing != null && !existing.isEmpty()) {
            int max = existing.stream().mapToInt(bd -> {
                String code = bd.getBedCode();
                try {
                    int idx = Integer.parseInt(code.replaceAll(".*[^0-9](?=\\d+$)", ""));
                    return idx;
                } catch (Exception ex) { return 0; }
            }).max().orElse(0);
            startIndex = max + 1;
        }

        for (int i = startIndex; i < startIndex + total; i++) {
            Bed b = new Bed();
            b.setHospitalId(hospitalId);
            b.setWardId(saved.getWardId());
            b.setBedCode(String.format("%s-B%d", req.getWardName(), i));
            b.setStatus("available");
            bedRepository.save(b);
        }

        return toResponse(saved);
    }

    @Transactional
    public List<WardResponse> bulkCreate(BulkCreateWardsRequest req) {
        return req.getWards().stream().map(this::createWard).collect(Collectors.toList());
    }

    public List<WardResponse> getAllWards() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return wardRepository.findByHospitalId(hospitalId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<BedResponse> getBedsForWard(Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return bedRepository.findByWardIdAndHospitalId(wardId, hospitalId)
                .stream().map(b -> {
                    BedResponse br = new BedResponse();
                    br.setBedId(b.getBedId());
                    br.setBedCode(b.getBedCode());
                    br.setStatus(b.getStatus());
                    return br;
                }).collect(Collectors.toList());
    }

    @Transactional
    public WardResponse updateWard(Long wardId, UpdateWardRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Ward w = wardRepository.findById(wardId).orElseThrow(() -> new RuntimeException("Ward not found"));
        if (!w.getHospitalId().equals(hospitalId)) throw new RuntimeException("Access denied");

        if (req.getWardName() != null) w.setWardName(req.getWardName());
        if (req.getBedPrice() != null) w.setBedPrice(req.getBedPrice());
        if (req.getFloorNumber() != null) w.setFloorNumber(req.getFloorNumber());

        Ward saved = wardRepository.save(w);
        return toResponse(saved);
    }

    private WardResponse toResponse(Ward w) {
        WardResponse r = new WardResponse();
        r.setWardId(w.getWardId());
        r.setWardName(w.getWardName());
        r.setBedPrice(w.getBedPrice());
        r.setTotalBeds(w.getTotalBeds());
        r.setFloorNumber(w.getFloorNumber());
        return r;
    }
}
