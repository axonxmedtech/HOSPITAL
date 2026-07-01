package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.FluidIntakeRequest;
import com.hms.dto.FluidOutputRequest;
import com.hms.entity.DailyFluidBalance;
import com.hms.entity.FluidIntake;
import com.hms.entity.FluidOutput;
import com.hms.service.hospital.FluidService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FluidController - Handles fluid intake and output monitoring, automatic derivations,
 * and trend query endpoints.
 *
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/fluid")
public class FluidController {

    @Autowired
    private FluidService fluidService;

    /**
     * Records a manual fluid intake observation. Allowed for Nurse or Dietician roles.
     */
    @PostMapping("/intake")
    @PreAuthorize("hasAnyRole('NURSE', 'DIETICIAN')")
    public ResponseEntity<ApiResponse<FluidIntake>> recordIntake(
            @Valid @RequestBody FluidIntakeRequest request) {
        FluidIntake intake = fluidService.recordIntake(
                request.getAdmissionId(),
                request.getType(),
                request.getVolumeMl(),
                request.getDescription()
        );
        return ResponseEntity.ok(ApiResponse.ok("Intake recorded successfully", intake));
    }

    /**
     * Records a manual fluid output observation. Allowed for Nurse role.
     */
    @PostMapping("/output")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<ApiResponse<FluidOutput>> recordOutput(
            @Valid @RequestBody FluidOutputRequest request) {
        FluidOutput output = fluidService.recordOutput(
                request.getAdmissionId(),
                request.getType(),
                request.getVolumeMl(),
                request.getColor(),
                request.getDescription()
        );
        return ResponseEntity.ok(ApiResponse.ok("Output recorded successfully", output));
    }

    /**
     * Retrieves the daily accumulated intake, output, and net balance cache for today.
     */
    @GetMapping("/balance/{admissionId}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<DailyFluidBalance>> getFluidBalance(
            @PathVariable Long admissionId) {
        DailyFluidBalance balance = fluidService.getFluidBalance(admissionId);
        return ResponseEntity.ok(ApiResponse.ok(balance));
    }

    /**
     * Retrieves day-by-day aggregated trends of fluid intake vs outputs.
     */
    @GetMapping("/trends/{admissionId}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<List<DailyFluidBalance>>> getFluidTrends(
            @PathVariable Long admissionId) {
        List<DailyFluidBalance> trends = fluidService.getFluidTrends(admissionId);
        return ResponseEntity.ok(ApiResponse.ok(trends));
    }
}
