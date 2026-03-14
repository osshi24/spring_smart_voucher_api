package com.smartvoucher.controller;

import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.entity.ApiRequestLog;
import com.smartvoucher.service.ApiRequestLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/request-logs")
@RequiredArgsConstructor
@Tag(name = "Request Log", description = "Theo dõi và tra cứu lịch sử API request từ POS và hệ thống bên ngoài")
public class RequestLogController {

    private final ApiRequestLogService apiRequestLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('REQUEST_LOG_READ')")
    @Operation(summary = "Tra cứu lịch sử API request với bộ lọc đầy đủ")
    public ResponseEntity<ApiResponse<Page<ApiRequestLog>>> getLogs(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) Integer responseStatus,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) Long responseTimeMsMin,
            @RequestParam(required = false) Long responseTimeMsMax,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                apiRequestLogService.findLogs(id, apiKeyId, method, endpoint, responseStatus,
                        ipAddress, responseTimeMsMin, responseTimeMsMax, from, to, pageable)));
    }
}
