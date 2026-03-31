package com.smartvoucher.controller;

import com.smartvoucher.dto.request.CustomerCreateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.BulkOperationResponse;
import com.smartvoucher.dto.response.CustomerResponse;
import com.smartvoucher.dto.response.CustomerUsageResponse;
import com.smartvoucher.dto.response.CustomerVoucherResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.service.CustomerService;
import com.smartvoucher.service.ExportService;
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

@Tag(name = "Khách hàng", description = "Quản lý khách hàng nhận và sử dụng voucher")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final ExportService exportService;

    @Operation(summary = "Tạo khách hàng mới")
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CustomerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(customerService.create(request)));
    }

    @Operation(summary = "Lấy danh sách khách hàng (có bộ lọc)")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getAll(
            @And({
                @Spec(spec = Equal.class,              params = "id",            path = "id"),
                @Spec(spec = LikeIgnoreCase.class,     params = "name",          path = "fullName"),
                @Spec(spec = LikeIgnoreCase.class,     params = "email",         path = "email"),
                @Spec(spec = LikeIgnoreCase.class,     params = "phone",         path = "phone"),
                @Spec(spec = Equal.class,              params = "externalId",    path = "externalId"),
                @Spec(spec = IsTrue.class,             params = "isActive",      path = "isActive"),
                @Spec(spec = GreaterThanOrEqual.class, params = "createdAtFrom", path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "createdAtTo",   path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = GreaterThanOrEqual.class, params = "updatedAtFrom", path = "updatedAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "updatedAtTo",   path = "updatedAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX")
            }) Specification<Customer> spec,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getAll(spec, pageable)));
    }

    @Operation(summary = "Lấy chi tiết khách hàng theo ID")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getById(id)));
    }

    @Operation(summary = "Tìm khách hàng theo mã ngoài (externalId từ hệ thống POS/e-commerce)")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping("/by-external/{externalId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByExternalId(@PathVariable String externalId) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getByExternalId(externalId)));
    }

    @Operation(summary = "Cập nhật thông tin khách hàng")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CustomerCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.update(id, request)));
    }

    @Operation(summary = "Lấy danh sách voucher đã gán cho khách hàng")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping("/{id}/vouchers")
    public ResponseEntity<ApiResponse<Page<CustomerVoucherResponse>>> getCustomerVouchers(
            @PathVariable Long id,
            @RequestParam(required = false) String voucherCode,
            @RequestParam(required = false) String discountType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                customerService.getCustomerVouchers(id, voucherCode, discountType, pageable)));
    }

    @Operation(summary = "Lấy lịch sử sử dụng voucher của khách hàng")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping("/{id}/usages")
    public ResponseEntity<ApiResponse<Page<CustomerUsageResponse>>> getCustomerUsages(
            @PathVariable Long id,
            @RequestParam(required = false) Long voucherId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime usedAtTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                customerService.getCustomerUsages(id, voucherId, usedAtFrom, usedAtTo, pageable)));
    }

    @Operation(summary = "Xóa khách hàng")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Vô hiệu hóa tài khoản khách hàng")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<CustomerResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.deactivate(id)));
    }

    @Operation(summary = "Kích hoạt lại tài khoản khách hàng đã bị vô hiệu hóa")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<CustomerResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.activate(id)));
    }

    @Operation(summary = "Xuất danh sách khách hàng ra file CSV")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @And({
                @Spec(spec = IsTrue.class, params = "isActive", path = "isActive"),
                @Spec(spec = LikeIgnoreCase.class, params = "name", path = "fullName")
            }) Specification<Customer> spec) {
        byte[] csv = exportService.exportCustomers(spec);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"customers.csv\"")
                .body(csv);
    }

    @Operation(summary = "Nhập khách hàng hàng loạt từ file CSV")
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> importCsv(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(customerService.importFromCsv(file)));
    }
}
