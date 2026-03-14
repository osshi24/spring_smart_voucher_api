package com.smartvoucher.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RedemptionReverseResponse {
    private Long usageId;
    private String voucherCode;
    private boolean reversed;
    private int newUsageCount;
}
