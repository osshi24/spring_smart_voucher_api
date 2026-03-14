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
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final VoucherAssignmentService voucherAssignmentService;

    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> create(@Valid @RequestBody VoucherCreateRequest request) {
        VoucherResponse response = voucherService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VoucherResponse>>> getAll(
            @And({
                @Spec(spec = Like.class, params = "code", path = "code"),
                @Spec(spec = Equal.class, params = "status", path = "status"),
                @Spec(spec = Equal.class, params = "discountType", path = "discountType"),
                @Spec(spec = Equal.class, params = "campaignId", path = "campaign.id")
            }) Specification<Voucher> spec,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<VoucherResponse> page = voucherService.getAll(spec, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getById(id)));
    }

    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody VoucherUpdateRequest request) {
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
            @PathVariable Long id,
            @RequestBody java.util.List<Long> customerIds) {
        voucherService.assignCustomers(id, customerIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}/customers")
    public ResponseEntity<ApiResponse<Page<VoucherCustomerResponse>>> getVoucherCustomers(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherAssignmentService.getVoucherCustomers(id, pageable)));
    }

    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    @DeleteMapping("/{id}/customers/{customerId}")
    public ResponseEntity<ApiResponse<String>> revokeAssignment(
            @PathVariable Long id,
            @PathVariable Long customerId) {
        voucherAssignmentService.revokeAssignment(id, customerId);
        return ResponseEntity.ok(ApiResponse.success("Assignment revoked."));
    }

    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    @GetMapping("/{id}/usages")
    public ResponseEntity<ApiResponse<Page<VoucherUsageResponse>>> getVoucherUsages(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherAssignmentService.getVoucherUsages(id, pageable)));
    }
}
