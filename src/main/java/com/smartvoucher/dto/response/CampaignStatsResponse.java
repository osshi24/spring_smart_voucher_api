package com.smartvoucher.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class CampaignStatsResponse {

    private Long campaignId;
    private String campaignName;
    private long totalVouchers;
    private long totalRedemptions;
    private BigDecimal totalDiscountAmount;
    private BigDecimal budget;
}
