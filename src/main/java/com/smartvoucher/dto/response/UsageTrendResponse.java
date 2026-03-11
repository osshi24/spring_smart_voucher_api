package com.smartvoucher.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class UsageTrendResponse {

    private String period;
    private long usageCount;
    private BigDecimal discountAmount;
}
