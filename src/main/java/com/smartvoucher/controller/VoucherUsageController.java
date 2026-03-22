package com.smartvoucher.controller;

import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.VoucherUsageResponse;
import com.smartvoucher.entity.VoucherUsage;
import com.smartvoucher.service.ExportService;
import com.smartvoucher.service.VoucherUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@Tag(name = "Usages", description = "Lịch sử sử dụng voucher toàn hệ thống")
@RestController
@RequestMapping("/api/v1/usages")
@RequiredArgsConstructor
public class VoucherUsageController {

    private final VoucherUsageService voucherUsageService;
    private final ExportService exportService;

    @Operation(summary = "Lấy toàn bộ lịch sử sử dụng voucher (có filter + phân trang)")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VoucherUsageResponse>>> getAll(
            @RequestParam(required = false) String externalOrderId,
            @RequestParam(required = false) String externalBranchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtTo,
            Pageable pageable) {

        Specification<VoucherUsage> spec = buildSpec(externalOrderId, externalBranchId, usedAtFrom, usedAtTo);
        return ResponseEntity.ok(ApiResponse.success(voucherUsageService.findAll(spec, pageable)));
    }

    @Operation(summary = "Xuất toàn bộ lịch sử sử dụng ra file CSV (có filter)")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String externalOrderId,
            @RequestParam(required = false) String externalBranchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtTo) {

        Specification<VoucherUsage> spec = buildSpec(externalOrderId, externalBranchId, usedAtFrom, usedAtTo);
        byte[] csv = exportService.exportAllUsages(spec);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"usages.csv\"")
                .body(csv);
    }


    private Specification<VoucherUsage> buildSpec(String externalOrderId, String externalBranchId,
                                                   OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (externalOrderId != null && !externalOrderId.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("externalOrderId")),
                        "%" + externalOrderId.toLowerCase() + "%"));
            }
            if (externalBranchId != null && !externalBranchId.isBlank()) {
                predicates.add(cb.equal(root.get("externalBranchId"), externalBranchId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("usedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("usedAt"), to));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
