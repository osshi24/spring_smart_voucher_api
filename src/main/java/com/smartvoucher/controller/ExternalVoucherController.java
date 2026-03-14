package com.smartvoucher.controller;

import com.smartvoucher.dto.request.VoucherRedeemRequest;
import com.smartvoucher.dto.response.RedemptionReverseResponse;
import com.smartvoucher.dto.request.VoucherValidateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.CustomerResponse;
import com.smartvoucher.dto.response.QrResolveResponse;
import com.smartvoucher.dto.response.VoucherValidateResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.service.ApiRequestLogService;
import com.smartvoucher.service.CustomerResolutionService;
import com.smartvoucher.service.QrTokenService;
import com.smartvoucher.service.VoucherRedemptionService;
import com.smartvoucher.service.VoucherValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/external/vouchers")
@RequiredArgsConstructor
@Tag(name = "POS - Voucher", description = "API dành cho POS và hệ thống bên ngoài: xác thực, đổi và hoàn tác voucher (xác thực bằng API key)")
public class ExternalVoucherController {

    private final VoucherValidationService voucherValidationService;
    private final VoucherRedemptionService voucherRedemptionService;
    private final ApiRequestLogService apiRequestLogService;
    private final QrTokenService qrTokenService;
    private final VoucherRepository voucherRepository;
    private final CustomerRepository customerRepository;
    private final CustomerResolutionService customerResolutionService;

    @Operation(summary = "Kiểm tra tính hợp lệ của voucher (không tiêu lượt dùng)")
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<VoucherValidateResponse>> validate(
            @Valid @RequestBody VoucherValidateRequest request,
            HttpServletRequest httpRequest) {

        Long apiKeyId = (Long) httpRequest.getAttribute("apiKeyId");
        VoucherValidateResponse response = voucherValidationService.validate(request, apiKeyId);

        apiRequestLogService.logRequest(
                "/api/external/vouchers/validate", "POST",
                request, 200, response,
                httpRequest.getRemoteAddr(), apiKeyId
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Áp dụng voucher cho đơn hàng (tiêu một lượt dùng, hỗ trợ idempotency)")
    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<VoucherValidateResponse>> redeem(
            @Valid @RequestBody VoucherRedeemRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {

        Long apiKeyId = (Long) httpRequest.getAttribute("apiKeyId");
        VoucherValidateResponse response = voucherRedemptionService.redeem(request, idempotencyKey, apiKeyId);

        apiRequestLogService.logRequest(
                "/api/external/vouchers/redeem", "POST",
                request, 200, response,
                httpRequest.getRemoteAddr(), apiKeyId
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Giải mã QR token để lấy thông tin voucher và khách hàng")
    @GetMapping("/qr/{token}")
    public ResponseEntity<ApiResponse<QrResolveResponse>> resolveQr(@PathVariable String token) {
        Claims claims = qrTokenService.resolveQrToken(token);

        String voucherCode = claims.get("voucherCode", String.class);
        Long customerId = claims.get("customerId", Long.class);

        Voucher voucher = voucherRepository.findByCode(voucherCode)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + voucherCode));

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

        QrResolveResponse response = QrResolveResponse.builder()
                .voucherCode(voucher.getCode())
                .voucherName(voucher.getDescription())
                .discountType(voucher.getDiscountType())
                .discountValue(voucher.getDiscountValue())
                .validUntil(voucher.getValidUntil())
                .customerId(customer.getId())
                .customerName(customer.getFullName())
                .status(voucher.getStatus())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Tra cứu thông tin khách hàng theo số điện thoại, email hoặc externalId")
    @GetMapping("/customers/lookup")
    public ResponseEntity<ApiResponse<CustomerResponse>> lookupCustomer(@RequestParam String ref) {
        Customer customer = customerResolutionService.resolve(null, ref, null, false);
        return ResponseEntity.ok(ApiResponse.success(CustomerResponse.from(customer)));
    }

    @Operation(summary = "Hoàn tác lượt dùng voucher khi đơn hàng bị hủy hoặc hoàn tiền")
    @PostMapping("/usages/{usageId}/reverse")
    public ResponseEntity<ApiResponse<RedemptionReverseResponse>> reverse(
            @PathVariable Long usageId,
            HttpServletRequest httpRequest) {
        Long apiKeyId = (Long) httpRequest.getAttribute("apiKeyId");
        RedemptionReverseResponse response = voucherRedemptionService.reverse(usageId, apiKeyId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
