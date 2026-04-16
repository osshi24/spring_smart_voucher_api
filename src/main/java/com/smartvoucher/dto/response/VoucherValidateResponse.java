package com.smartvoucher.dto.response;

import com.smartvoucher.entity.enums.DiscountType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class VoucherValidateResponse {

    private boolean valid;
    private String voucherCode;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private BigDecimal maxDiscountAmount;
    private String message;
    private Boolean idempotent;

    public static VoucherValidateResponse valid(String code, DiscountType type, BigDecimal value,
                                                 BigDecimal amount, BigDecimal maxAmount) {
        VoucherValidateResponse res = new VoucherValidateResponse();
        res.valid = true;
        res.voucherCode = code;
        res.discountType = type;
        res.discountValue = value;
        res.discountAmount = amount;
        res.maxDiscountAmount = maxAmount;
        res.message = "Voucher is valid";
        return res;
    }

    public static VoucherValidateResponse redeemed(String code, DiscountType type, BigDecimal value,
                                                    BigDecimal amount, BigDecimal maxAmount) {
        VoucherValidateResponse res = valid(code, type, value, amount, maxAmount);
        res.message = "Voucher redeemed successfully";
        return res;
    }

    public static VoucherValidateResponse invalid(String message) {
        VoucherValidateResponse res = new VoucherValidateResponse();
        res.valid = false;
        res.message = message;
        return res;
    }
}
