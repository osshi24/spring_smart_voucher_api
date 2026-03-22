package com.smartvoucher.dto.response;

import com.smartvoucher.entity.VoucherUsage;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class VoucherUsageResponse {

    private Long id;
    private Long voucherId;
    private String voucherCode;
    private Long customerId;
    private String customerName;
    private String externalOrderId;
    private String externalBranchId;
    private BigDecimal discountAmount;
    private BigDecimal orderTotal;
    private String status;
    private OffsetDateTime usedAt;

    public static VoucherUsageResponse from(VoucherUsage usage) {
        VoucherUsageResponse res = new VoucherUsageResponse();
        res.id = usage.getId();
        if (usage.getVoucher() != null) {
            res.voucherId = usage.getVoucher().getId();
            res.voucherCode = usage.getVoucher().getCode();
        }
        if (usage.getCustomer() != null) {
            res.customerId = usage.getCustomer().getId();
            res.customerName = usage.getCustomer().getFullName();
        }
        res.externalOrderId = usage.getExternalOrderId();
        res.externalBranchId = usage.getExternalBranchId();
        res.discountAmount = usage.getDiscountAmount();
        res.orderTotal = usage.getOrderTotal();
        res.status = "COMPLETED";
        res.usedAt = usage.getUsedAt();
        return res;
    }
}
