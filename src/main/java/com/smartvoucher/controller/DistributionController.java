package com.smartvoucher.controller;

import com.smartvoucher.dto.request.DistributionCreateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.DistributionResponse;
import com.smartvoucher.entity.enums.DistributionStatus;
import com.smartvoucher.service.DistributionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/distributions")
@RequiredArgsConstructor
public class DistributionController {

    private final DistributionService distributionService;

    @PreAuthorize("hasAuthority('DISTRIBUTION_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<DistributionResponse>> create(
            @Valid @RequestBody DistributionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(distributionService.create(request)));
    }

    @PreAuthorize("hasAuthority('DISTRIBUTION_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DistributionResponse>>> getAll(
            @RequestParam(required = false) Long voucherId,
            @RequestParam(required = false) DistributionStatus status) {
        if (voucherId != null) {
            return ResponseEntity.ok(ApiResponse.success(distributionService.getByVoucher(voucherId)));
        }
        if (status != null) {
            return ResponseEntity.ok(ApiResponse.success(distributionService.getByStatus(status)));
        }
        return ResponseEntity.ok(ApiResponse.success(distributionService.getByVoucher(null)));
    }

    @PreAuthorize("hasAuthority('DISTRIBUTION_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DistributionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(distributionService.getById(id)));
    }

    @PreAuthorize("hasAuthority('DISTRIBUTION_CREATE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> cancel(@PathVariable Long id) {
        distributionService.cancel(id);
        return ResponseEntity.ok(ApiResponse.success("Distribution cancelled."));
    }
}
