package com.smartvoucher.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class DashboardOverviewResponse {

    private long totalVouchers;
    private long activeVouchers;
    private long totalUsages;
    private BigDecimal totalDiscountAmount;
    private List<VoucherResponse> topVouchers;
}
