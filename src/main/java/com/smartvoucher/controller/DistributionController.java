package com.smartvoucher.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoucher.dto.request.DistributionCreateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.BulkDistributionResponse;
import com.smartvoucher.dto.response.DistributionResponse;
import com.smartvoucher.entity.VoucherDistribution;
import com.smartvoucher.service.DistributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.And;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Tag(name = "Phân phối", description = "Quản lý việc gửi voucher đến khách hàng qua email/SMS")
@RestController
@RequestMapping("/api/v1/distributions")
@RequiredArgsConstructor
public class DistributionController {

    private final DistributionService distributionService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Operation(summary = "Tạo lệnh phân phối voucher — truyền object để tạo một, truyền array để tạo nhiều")
    @PreAuthorize("hasAuthority('DISTRIBUTION_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@RequestBody JsonNode body) {
        if (body.isArray()) {
            List<DistributionCreateRequest> requests = new ArrayList<>();
            for (JsonNode node : body) {
                DistributionCreateRequest req = objectMapper.convertValue(node, DistributionCreateRequest.class);
                Set<ConstraintViolation<DistributionCreateRequest>> violations = validator.validate(req);
                if (!violations.isEmpty()) {
                    String reason = violations.iterator().next().getMessage();
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("VALIDATION_ERROR", reason));
                }
                requests.add(req);
            }
            BulkDistributionResponse result = distributionService.createBulk(requests);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
        } else {
            DistributionCreateRequest req = objectMapper.convertValue(body, DistributionCreateRequest.class);
            Set<ConstraintViolation<DistributionCreateRequest>> violations = validator.validate(req);
            if (!violations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("VALIDATION_ERROR", violations.iterator().next().getMessage()));
            }
            DistributionResponse result = distributionService.create(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
        }
    }

    @Operation(summary = "Lấy danh sách lệnh phân phối (có bộ lọc)")
    @PreAuthorize("hasAuthority('DISTRIBUTION_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<DistributionResponse>>> getAll(
            @And({
                @Spec(spec = Equal.class,              params = "id",            path = "id"),
                @Spec(spec = Equal.class,              params = "voucherId",     path = "voucher.id"),
                @Spec(spec = Equal.class,              params = "customerId",    path = "customer.id"),
                @Spec(spec = Equal.class,              params = "status",        path = "status"),
                @Spec(spec = Equal.class,              params = "channel",       path = "channel"),
                @Spec(spec = GreaterThanOrEqual.class, params = "sentAtFrom",    path = "sentAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "sentAtTo",      path = "sentAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = GreaterThanOrEqual.class, params = "createdAtFrom", path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "createdAtTo",   path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX")
            }) Specification<VoucherDistribution> spec,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(distributionService.getAll(spec, pageable)));
    }

    @Operation(summary = "Lấy chi tiết lệnh phân phối theo ID")
    @PreAuthorize("hasAuthority('DISTRIBUTION_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DistributionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(distributionService.getById(id)));
    }

    @Operation(summary = "Hủy lệnh phân phối chưa gửi")
    @PreAuthorize("hasAuthority('DISTRIBUTION_CREATE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> cancel(@PathVariable Long id) {
        distributionService.cancel(id);
        return ResponseEntity.ok(ApiResponse.success("Distribution cancelled."));
    }

    @Operation(summary = "Thử lại gửi voucher cho lệnh phân phối thất bại (FAILED)")
    @PreAuthorize("hasAuthority('DISTRIBUTION_CREATE')")
    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<DistributionResponse>> retry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(distributionService.retry(id)));
    }

    @Operation(summary = "Gửi lại email voucher cho khách hàng (áp dụng cả khi status là SENT)")
    @PreAuthorize("hasAuthority('DISTRIBUTION_CREATE')")
    @PostMapping("/{id}/resend")
    public ResponseEntity<ApiResponse<DistributionResponse>> resend(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(distributionService.resend(id)));
    }
}
