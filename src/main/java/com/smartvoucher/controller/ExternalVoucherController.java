package com.smartvoucher.controller;

import com.smartvoucher.dto.request.VoucherRedeemRequest;
import com.smartvoucher.dto.request.VoucherValidateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.VoucherValidateResponse;
import com.smartvoucher.service.ApiRequestLogService;
import com.smartvoucher.service.VoucherRedemptionService;
import com.smartvoucher.service.VoucherValidationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/external/vouchers")
@RequiredArgsConstructor
public class ExternalVoucherController {

    private final VoucherValidationService voucherValidationService;
    private final VoucherRedemptionService voucherRedemptionService;
    private final ApiRequestLogService apiRequestLogService;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<VoucherValidateResponse>> validate(
            @Valid @RequestBody VoucherValidateRequest request,
            HttpServletRequest httpRequest) {

        VoucherValidateResponse response = voucherValidationService.validate(request);

        Long apiKeyId = (Long) httpRequest.getAttribute("apiKeyId");
        apiRequestLogService.logRequest(
                "/api/external/vouchers/validate", "POST",
                request, 200, response,
                httpRequest.getRemoteAddr(), apiKeyId
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<VoucherValidateResponse>> redeem(
            @Valid @RequestBody VoucherRedeemRequest request,
            HttpServletRequest httpRequest) {

        VoucherValidateResponse response = voucherRedemptionService.redeem(request);

        Long apiKeyId = (Long) httpRequest.getAttribute("apiKeyId");
        apiRequestLogService.logRequest(
                "/api/external/vouchers/redeem", "POST",
                request, 200, response,
                httpRequest.getRemoteAddr(), apiKeyId
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
