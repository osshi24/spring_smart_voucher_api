package com.smartvoucher.controller;

import com.smartvoucher.dto.request.MerchantProfileRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.MerchantProfileResponse;
import com.smartvoucher.service.MerchantProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Merchant Profile", description = "Quản lý thông tin hồ sơ doanh nghiệp của merchant")
@RestController
@RequestMapping("/api/v1/merchant/profile")
@RequiredArgsConstructor
public class MerchantProfileController {

    private final MerchantProfileService merchantProfileService;

    @Operation(summary = "Lấy thông tin hồ sơ merchant hiện tại")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<MerchantProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success(merchantProfileService.getProfile()));
    }

    @Operation(summary = "Cập nhật thông tin hồ sơ merchant (tên, địa chỉ, logo, mã số thuế)")
    @PreAuthorize("isAuthenticated()")
    @PutMapping
    public ResponseEntity<ApiResponse<MerchantProfileResponse>> updateProfile(
            @RequestBody MerchantProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.success(merchantProfileService.updateProfile(req)));
    }
}
