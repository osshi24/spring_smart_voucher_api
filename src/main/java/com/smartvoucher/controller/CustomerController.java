package com.smartvoucher.controller;

import com.smartvoucher.dto.request.CustomerCreateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.CustomerResponse;
import com.smartvoucher.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CustomerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(customerService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getAll(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable Long id, @RequestBody CustomerCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
