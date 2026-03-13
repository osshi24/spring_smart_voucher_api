package com.smartvoucher.controller;

import com.smartvoucher.dto.request.ApiKeyCreateRequest;
import com.smartvoucher.dto.response.ApiKeyResponse;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PreAuthorize("hasAuthority('APIKEY_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<ApiKeyResponse>> create(@Valid @RequestBody ApiKeyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(apiKeyService.create(request)));
    }

    @PreAuthorize("hasAuthority('APIKEY_CREATE') or hasAuthority('APIKEY_DEACTIVATE')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.getAll()));
    }

    @PreAuthorize("hasAuthority('APIKEY_DEACTIVATE')")
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.deactivate(id)));
    }
}
