package com.smartvoucher.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class DashboardOverviewResponse {

    private long totalVouchers;
    private long activeVouchers;
    private long totalUsages;
    private BigDecimal totalDiscountAmount;
    private List<VoucherResponse> topVouchers;
    private double conversionRate;
    private Map<String, BigDecimal> revenueByDay;
    private Long activeMerchantCount;
    private Long activeCustomerCount;
    private Double savingsGrowthRate;
    private Double activeUsersGrowthRate;
    private Double redemptionRateGrowth;
}
