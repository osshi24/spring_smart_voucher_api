package com.smartvoucher.dto.response;

import com.smartvoucher.entity.VoucherUsage;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class CustomerUsageResponse {

    private Long id;
    private Long voucherId;
    private String voucherCode;
    private String externalOrderId;
    private BigDecimal discountAmount;
    private BigDecimal orderTotal;
    private OffsetDateTime usedAt;

    public static CustomerUsageResponse from(VoucherUsage usage) {
        CustomerUsageResponse res = new CustomerUsageResponse();
        res.id = usage.getId();
        if (usage.getVoucher() != null) {
            res.voucherId = usage.getVoucher().getId();
            res.voucherCode = usage.getVoucher().getCode();
        }
        res.externalOrderId = usage.getExternalOrderId();
        res.discountAmount = usage.getDiscountAmount();
        res.orderTotal = usage.getOrderTotal();
        res.usedAt = usage.getUsedAt();
        return res;
    }
}
