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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/distributions")
@RequiredArgsConstructor
public class DistributionController {

    private final DistributionService distributionService;

    @PostMapping
    public ResponseEntity<ApiResponse<DistributionResponse>> create(
            @Valid @RequestBody DistributionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(distributionService.create(request)));
    }

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
}
