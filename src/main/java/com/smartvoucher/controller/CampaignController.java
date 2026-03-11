package com.smartvoucher.controller;

import com.smartvoucher.dto.request.CampaignCreateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.CampaignResponse;
import com.smartvoucher.dto.response.CampaignStatsResponse;
import com.smartvoucher.entity.enums.CampaignStatus;
import com.smartvoucher.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    public ResponseEntity<ApiResponse<CampaignResponse>> create(@Valid @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(campaignService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CampaignResponse>>> getAll(
            @RequestParam(required = false) CampaignStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.getAll(status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> update(
            @PathVariable Long id, @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.update(id, request)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CampaignResponse>> updateStatus(
            @PathVariable Long id, @RequestParam CampaignStatus status) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.updateStatus(id, status)));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponse<CampaignStatsResponse>> getStats(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.getStats(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
