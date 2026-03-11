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

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Order total is required")
    @Positive(message = "Order total must be positive")
    private BigDecimal orderTotal;

    private List<String> products;

    private String categoryId;

    private String branchId;
}
