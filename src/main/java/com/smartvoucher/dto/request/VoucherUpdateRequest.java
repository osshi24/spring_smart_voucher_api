package com.smartvoucher.dto.request;

import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class VoucherUpdateRequest {

    private String description;

    private DiscountType discountType;

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

    private Boolean isPublic;

    private OffsetDateTime validFrom;

    private OffsetDateTime validUntil;

    private VoucherStatus status;
}
