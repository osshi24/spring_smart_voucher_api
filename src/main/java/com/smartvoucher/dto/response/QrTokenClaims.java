package com.smartvoucher.dto.response;

public record QrTokenClaims(String voucherCode, Long customerId, Long merchantId) {
}
