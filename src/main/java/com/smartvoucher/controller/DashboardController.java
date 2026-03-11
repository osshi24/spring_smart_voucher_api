package com.smartvoucher.controller;

import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.CampaignStatsResponse;
import com.smartvoucher.dto.response.DashboardOverviewResponse;
import com.smartvoucher.dto.response.UsageTrendResponse;
import com.smartvoucher.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getOverview()));
    }

    @GetMapping("/campaigns/{id}")
    public ResponseEntity<ApiResponse<CampaignStatsResponse>> getCampaignStats(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getCampaignStats(id)));
    }

    @GetMapping("/usage-trend")
    public ResponseEntity<ApiResponse<List<UsageTrendResponse>>> getUsageTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false, defaultValue = "DAY") String groupBy) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getUsageTrend(from, to, groupBy)));
    }

    @GetMapping("/branch-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBranchStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getBranchStats(from, to)));
    }
}
