package com.hms.service.hospital;

import com.hms.entity.IpdPackage;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.IpdPackageRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** The hospital's IPD packages. The admin manages them; everyone at the desk can list active ones. */
@Service
public class IpdPackageService {

    private final IpdPackageRepository repository;
    private final SecurityContextHelper securityHelper;
    private final com.hms.service.AuditLogService auditLogService;

    public IpdPackageService(IpdPackageRepository repository, SecurityContextHelper securityHelper,
                             com.hms.service.AuditLogService auditLogService) {
        this.repository = repository;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<IpdPackage> list(boolean includeInactive) {
        Long hospitalId = requireHospital();
        return includeInactive ? repository.findByHospitalIdOrderByNameAsc(hospitalId)
                : repository.findByHospitalIdAndIsActiveTrueOrderByNameAsc(hospitalId);
    }

    /** An active package of the caller's hospital, for applying to an admission. */
    @Transactional(readOnly = true)
    public IpdPackage requireActive(Long id) {
        IpdPackage p = repository.findByIdAndHospitalId(id, requireHospital())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
        if (!Boolean.TRUE.equals(p.getIsActive())) {
            throw new IllegalArgumentException("The package \"" + p.getName() + "\" is no longer offered");
        }
        return p;
    }

    /** Any package of the caller's hospital (an admission may refer to a retired one). */
    @Transactional(readOnly = true)
    public java.util.Optional<IpdPackage> find(Long id, Long hospitalId) {
        return id == null ? java.util.Optional.empty() : repository.findByIdAndHospitalId(id, hospitalId);
    }

    @Transactional
    public IpdPackage create(IpdPackage incoming) {
        requireAdmin();
        Long hospitalId = requireHospital();
        IpdPackage p = new IpdPackage();
        p.setHospitalId(hospitalId);
        fill(p, incoming);
        if (repository.existsByHospitalIdAndNameIgnoreCase(hospitalId, p.getName())) {
            throw new IllegalArgumentException("A package named \"" + p.getName() + "\" already exists");
        }
        IpdPackage saved = repository.save(p);
        audit("IPD_PACKAGE_CREATED", saved);
        return saved;
    }

    /** Price and days changes apply to admissions the package is applied to from now on. */
    @Transactional
    public IpdPackage update(Long id, IpdPackage incoming) {
        requireAdmin();
        Long hospitalId = requireHospital();
        IpdPackage p = repository.findByIdAndHospitalId(id, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
        fill(p, incoming);
        if (repository.existsByHospitalIdAndNameIgnoreCaseAndIdNot(hospitalId, p.getName(), id)) {
            throw new IllegalArgumentException("A package named \"" + p.getName() + "\" already exists");
        }
        if (incoming.getIsActive() != null) p.setIsActive(incoming.getIsActive());
        p.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        IpdPackage saved = repository.save(p);
        audit("IPD_PACKAGE_UPDATED", saved);
        return saved;
    }

    private static void fill(IpdPackage p, IpdPackage in) {
        String name = in.getName() == null ? "" : in.getName().trim();
        if (name.isEmpty() || name.length() > 120) throw new IllegalArgumentException("Enter a package name (up to 120 characters)");
        BigDecimal price = in.getPrice();
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Enter the package price");
        if (price.compareTo(new BigDecimal("10000000")) > 0) throw new IllegalArgumentException("The package price is too large");
        Integer days = in.getIncludedDays();
        if (days == null || days < 1 || days > 60) throw new IllegalArgumentException("Included days must be 1 to 60");
        String desc = in.getDescription() == null || in.getDescription().isBlank() ? null : in.getDescription().trim();
        if (desc != null && desc.length() > 500) throw new IllegalArgumentException("The description is too long");
        p.setName(name);
        p.setPrice(price.setScale(2, java.math.RoundingMode.HALF_UP));
        p.setIncludedDays(days);
        p.setDescription(desc);
    }

    private void audit(String action, IpdPackage p) {
        try {
            auditLogService.logAction(action, "IPD package " + p.getName() + ": " + p.getPrice() + " for "
                    + p.getIncludedDays() + " day(s)" + (Boolean.TRUE.equals(p.getIsActive()) ? "" : " (inactive)"),
                    securityHelper.getCurrentUserEmail(), p.getHospitalId(), "IPD_PACKAGE", String.valueOf(p.getId()), null);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(IpdPackageService.class).warn("Failed to audit {}", action, e);
        }
    }

    private void requireAdmin() {
        if (!"HOSPITAL_ADMIN".equalsIgnoreCase(securityHelper.getCurrentUserRole())) {
            throw new AccessDeniedException("Only the hospital admin can manage packages");
        }
    }

    private Long requireHospital() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
        return hospitalId;
    }
}
