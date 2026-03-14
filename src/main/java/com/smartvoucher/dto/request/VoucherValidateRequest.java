package com.smartvoucher.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class VoucherValidateRequest {

    @NotBlank(message = "Voucher code is required")
    private String voucherCode;

    /** Internal customerId — mutually exclusive with customerRef */
    private Long customerId;

    /** Flexible identifier: email, phone, externalId, or internal ID as string */
    private String customerRef;

    @NotNull(message = "Order total is required")
    @Positive(message = "Order total must be positive")
    private BigDecimal orderTotal;

    private List<String> products;

    private String categoryId;

    private String branchId;
}
