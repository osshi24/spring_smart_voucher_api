package com.smartvoucher.dto.response;

import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
public class QrResolveResponse {
    private String voucherCode;
    private String voucherName;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private OffsetDateTime validUntil;
    private Long customerId;
    private String customerName;
    private VoucherStatus status;
}
