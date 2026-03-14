package com.smartvoucher.dto.response;

import com.smartvoucher.entity.VoucherCustomer;
import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class CustomerVoucherResponse {

    private Long assignmentId;
    private Long voucherId;
    private String voucherCode;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private VoucherStatus status;
    private OffsetDateTime validFrom;
    private OffsetDateTime validUntil;
    private Boolean hasBeenUsed;

    public static CustomerVoucherResponse from(VoucherCustomer vc, boolean hasBeenUsed) {
        CustomerVoucherResponse res = new CustomerVoucherResponse();
        res.assignmentId = vc.getId();
        if (vc.getVoucher() != null) {
            res.voucherId = vc.getVoucher().getId();
            res.voucherCode = vc.getVoucher().getCode();
            res.discountType = vc.getVoucher().getDiscountType();
            res.discountValue = vc.getVoucher().getDiscountValue();
            res.status = vc.getVoucher().getStatus();
            res.validFrom = vc.getVoucher().getValidFrom();
            res.validUntil = vc.getVoucher().getValidUntil();
        }
        res.hasBeenUsed = hasBeenUsed;
        return res;
    }
}
