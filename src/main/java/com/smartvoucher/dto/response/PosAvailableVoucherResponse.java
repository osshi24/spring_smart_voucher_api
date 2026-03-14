package com.smartvoucher.dto.response;

import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.enums.DiscountType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class PosAvailableVoucherResponse {

    private String voucherCode;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderValue;
    private OffsetDateTime validUntil;
    private Boolean isPublic;

    public static PosAvailableVoucherResponse from(Voucher v) {
        PosAvailableVoucherResponse res = new PosAvailableVoucherResponse();
        res.voucherCode = v.getCode();
        res.description = v.getDescription();
        res.discountType = v.getDiscountType();
        res.discountValue = v.getDiscountValue();
        res.maxDiscountAmount = v.getMaxDiscountAmount();
        res.minOrderValue = v.getMinOrderValue();
        res.validUntil = v.getValidUntil();
        res.isPublic = v.getIsPublic();
        return res;
    }
}
