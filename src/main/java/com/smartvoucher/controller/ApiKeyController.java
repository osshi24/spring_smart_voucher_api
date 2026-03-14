package com.smartvoucher.controller;

import com.smartvoucher.dto.request.ApiKeyCreateRequest;
import com.smartvoucher.dto.request.RateLimitUpdateRequest;
import com.smartvoucher.dto.response.ApiKeyResponse;
import com.smartvoucher.dto.response.ApiKeyUsageResponse;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.entity.ApiKey;
import com.smartvoucher.service.ApiKeyService;
import jakarta.validation.Valid;
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
    public ResponseEntity<ApiResponse<Page<ApiKeyResponse>>> getAll(
            @And({
                @Spec(spec = Equal.class,              params = "id",              path = "id"),
                @Spec(spec = Like.class,               params = "name",            path = "name"),
                @Spec(spec = Like.class,               params = "systemName",      path = "systemName"),
                @Spec(spec = IsTrue.class,             params = "isActive",        path = "isActive"),
                @Spec(spec = GreaterThanOrEqual.class, params = "rateLimitMinMin", path = "rateLimitPerMinute"),
                @Spec(spec = LessThanOrEqual.class,    params = "rateLimitMinMax", path = "rateLimitPerMinute"),
                @Spec(spec = GreaterThanOrEqual.class, params = "rateLimitDayMin", path = "rateLimitPerDay"),
                @Spec(spec = LessThanOrEqual.class,    params = "rateLimitDayMax", path = "rateLimitPerDay"),
                @Spec(spec = GreaterThanOrEqual.class, params = "expiresAtFrom",   path = "expiresAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "expiresAtTo",     path = "expiresAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = GreaterThanOrEqual.class, params = "createdAtFrom",   path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "createdAtTo",     path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX")
            }) Specification<ApiKey> spec,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.getAll(spec, pageable)));
    }

    @PreAuthorize("hasAuthority('APIKEY_CREATE')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.getById(id)));
    }

    @PreAuthorize("hasAuthority('APIKEY_CREATE')")
    @GetMapping("/{id}/usage")
    public ResponseEntity<ApiResponse<ApiKeyUsageResponse>> getUsage(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.getUsage(id)));
    }

    @PreAuthorize("hasAuthority('APIKEY_CREATE')")
    @PutMapping("/{id}/rate-limit")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> updateRateLimit(
            @PathVariable Long id, @Valid @RequestBody RateLimitUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.updateRateLimit(id, request)));
    }

    @PreAuthorize("hasAuthority('APIKEY_DEACTIVATE')")
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.deactivate(id)));
    }
}
