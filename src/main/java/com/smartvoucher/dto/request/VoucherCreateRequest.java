package com.smartvoucher.dto.request;

import com.smartvoucher.entity.enums.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class VoucherCreateRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must not exceed 50 characters")
    private String code;

    private Long campaignId;

    private String description;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Discount value must be positive")
    private BigDecimal discountValue;

    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0.0", message = "Min order value must be non-negative")
    private BigDecimal minOrderValue;

    private List<String> applicableProducts;

    private List<String> applicableCategories;

    private List<String> applicableBranches;

    private Integer maxUsageTotal;

    private Integer maxUsagePerCustomer;

    private Boolean isPublic = true;

    @NotNull(message = "Valid from date is required")
    private OffsetDateTime validFrom;

    @NotNull(message = "Valid until date is required")
    private OffsetDateTime validUntil;
}
