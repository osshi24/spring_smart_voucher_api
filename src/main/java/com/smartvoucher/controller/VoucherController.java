package com.smartvoucher.controller;

import com.smartvoucher.dto.request.VoucherCreateRequest;
import com.smartvoucher.dto.request.VoucherUpdateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.VoucherCustomerResponse;
import com.smartvoucher.dto.response.VoucherResponse;
import com.smartvoucher.dto.response.VoucherUsageResponse;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.service.VoucherAssignmentService;
import com.smartvoucher.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.And;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final VoucherAssignmentService voucherAssignmentService;

    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> create(@Valid @RequestBody VoucherCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(voucherService.create(request)));
    }

    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VoucherResponse>>> getAll(
            @And({
                @Spec(spec = Equal.class,              params = "id",              path = "id"),
                @Spec(spec = Like.class,               params = "code",            path = "code"),
                @Spec(spec = Like.class,               params = "description",     path = "description"),
                @Spec(spec = Equal.class,              params = "status",          path = "status"),
                @Spec(spec = Equal.class,              params = "discountType",    path = "discountType"),
                @Spec(spec = Equal.class,              params = "campaignId",      path = "campaign.id"),
                @Spec(spec = IsTrue.class,             params = "isPublic",        path = "isPublic"),
                @Spec(spec = GreaterThanOrEqual.class, params = "discountValueMin",path = "discountValue"),
                @Spec(spec = LessThanOrEqual.class,    params = "discountValueMax",path = "discountValue"),
                @Spec(spec = GreaterThanOrEqual.class, params = "minOrderValue",   path = "minOrderValue"),
                @Spec(spec = GreaterThanOrEqual.class, params = "maxUsageTotal",   path = "maxUsageTotal"),
                @Spec(spec = GreaterThanOrEqual.class, params = "validFrom",       path = "validFrom",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "validUntil",      path = "validUntil",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = GreaterThanOrEqual.class, params = "createdAtFrom",   path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "createdAtTo",     path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX")
            }) Specification<Voucher> spec,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getAll(spec, pageable)));
    }

    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getById(id)));
    }

    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> update(
            @PathVariable Long id, @Valid @RequestBody VoucherUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.update(id, request)));
    }

    @PreAuthorize("hasAuthority('VOUCHER_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        voucherService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @PostMapping("/{id}/customers")
    public ResponseEntity<ApiResponse<Void>> assignCustomers(
            @PathVariable Long id, @RequestBody List<Long> customerIds) {
        voucherService.assignCustomers(id, customerIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}/customers")
    public ResponseEntity<ApiResponse<Page<VoucherCustomerResponse>>> getVoucherCustomers(
            @PathVariable Long id,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerEmail,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherAssignmentService.getVoucherCustomers(id, customerName, customerEmail, pageable)));
    }

    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @DeleteMapping("/{id}/customers/{customerId}")
    public ResponseEntity<ApiResponse<String>> revokeAssignment(
            @PathVariable Long id, @PathVariable Long customerId) {
        voucherAssignmentService.revokeAssignment(id, customerId);
        return ResponseEntity.ok(ApiResponse.success("Assignment revoked."));
    }

    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}/usages")
    public ResponseEntity<ApiResponse<Page<VoucherUsageResponse>>> getVoucherUsages(
            @PathVariable Long id,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String externalOrderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherAssignmentService.getVoucherUsages(id, customerId, externalOrderId, usedAtFrom, usedAtTo, pageable)));
    }
}
