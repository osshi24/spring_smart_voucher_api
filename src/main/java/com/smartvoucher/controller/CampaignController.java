package com.smartvoucher.controller;

import com.smartvoucher.dto.request.CampaignCreateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.CampaignResponse;
import com.smartvoucher.dto.response.CampaignStatsResponse;
import com.smartvoucher.entity.Campaign;
import com.smartvoucher.entity.enums.CampaignStatus;
import com.smartvoucher.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.kaczmarzyk.spring.data.jpa.web.annotation.And;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.Like;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @PreAuthorize("hasAuthority('CAMPAIGN_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CampaignResponse>> create(@Valid @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(campaignService.create(request)));
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CampaignResponse>>> getAll(
            @And({
                @Spec(spec = Like.class, params = "name", path = "name"),
                @Spec(spec = Equal.class, params = "status", path = "status")
            }) Specification<Campaign> spec,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.getAll(spec, pageable)));
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.getById(id)));
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> update(
            @PathVariable Long id, @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.update(id, request)));
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_UPDATE')")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CampaignResponse>> updateStatus(
            @PathVariable Long id, @RequestParam CampaignStatus status) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.updateStatus(id, status)));
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponse<CampaignStatsResponse>> getStats(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.getStats(id)));
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
