package com.smartvoucher.event;

import com.smartvoucher.entity.enums.DiscountType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
public class VoucherRedeemedEvent extends ApplicationEvent {

    private final Long customerId;
    private final String customerEmail;
    private final String voucherCode;
    private final BigDecimal discountValue;
    private final DiscountType discountType;
    private final OffsetDateTime expiryDate;

    public VoucherRedeemedEvent(Object source, Long customerId, String customerEmail,
                                 String voucherCode, BigDecimal discountValue,
                                 DiscountType discountType, OffsetDateTime expiryDate) {
        super(source);
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.voucherCode = voucherCode;
        this.discountValue = discountValue;
        this.discountType = discountType;
        this.expiryDate = expiryDate;
    }
}
