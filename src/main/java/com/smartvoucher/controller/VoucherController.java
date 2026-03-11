package com.smartvoucher.controller;

import com.smartvoucher.dto.request.VoucherCreateRequest;
import com.smartvoucher.dto.request.VoucherUpdateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.VoucherResponse;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> create(@Valid @RequestBody VoucherCreateRequest request) {
        VoucherResponse response = voucherService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<VoucherResponse>>> getAll(
            @RequestParam(required = false) VoucherStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<VoucherResponse> page = voucherService.getAll(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody VoucherUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        voucherService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/customers")
    public ResponseEntity<ApiResponse<Void>> assignCustomers(
            @PathVariable Long id,
            @RequestBody java.util.List<Long> customerIds) {
        voucherService.assignCustomers(id, customerIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
