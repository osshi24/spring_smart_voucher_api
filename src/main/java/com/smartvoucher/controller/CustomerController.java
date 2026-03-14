package com.smartvoucher.controller;

import com.smartvoucher.dto.request.CustomerCreateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.CustomerResponse;
import com.smartvoucher.dto.response.CustomerUsageResponse;
import com.smartvoucher.dto.response.CustomerVoucherResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.service.CustomerService;
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

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CustomerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(customerService.create(request)));
    }

    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getAll(
            @And({
                @Spec(spec = Equal.class,              params = "id",            path = "id"),
                @Spec(spec = Like.class,               params = "name",          path = "fullName"),
                @Spec(spec = Like.class,               params = "email",         path = "email"),
                @Spec(spec = Like.class,               params = "phone",         path = "phone"),
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

    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getById(id)));
    }

    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping("/by-external/{externalId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByExternalId(@PathVariable String externalId) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getByExternalId(externalId)));
    }

    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CustomerCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.update(id, request)));
    }

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

    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
