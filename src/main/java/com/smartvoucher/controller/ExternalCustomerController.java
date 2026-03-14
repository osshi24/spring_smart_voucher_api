package com.smartvoucher.controller;

import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.PosAvailableVoucherResponse;
import com.smartvoucher.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/external/customers")
@RequiredArgsConstructor
@Tag(name = "POS - Khách hàng", description = "API dành cho POS: tra cứu voucher khả dụng của khách hàng (xác thực bằng API key)")
public class ExternalCustomerController {

    private final CustomerService customerService;

    @Operation(summary = "Tra cứu danh sách voucher đang có hiệu lực của khách hàng tại điểm bán")
    @GetMapping("/{ref}/vouchers")
    public ResponseEntity<ApiResponse<List<PosAvailableVoucherResponse>>> getAvailableVouchers(
            @PathVariable String ref,
            @RequestParam(required = false) BigDecimal orderTotal) {
        List<PosAvailableVoucherResponse> vouchers =
                customerService.getAvailableVouchersForCustomer(ref, orderTotal);
        return ResponseEntity.ok(ApiResponse.success(vouchers));
    }
}
