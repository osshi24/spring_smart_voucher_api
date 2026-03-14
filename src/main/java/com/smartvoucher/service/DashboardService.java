package com.smartvoucher.service;

import com.smartvoucher.dto.response.CampaignStatsResponse;
import com.smartvoucher.dto.response.DashboardOverviewResponse;
import com.smartvoucher.dto.response.UsageTrendResponse;
import com.smartvoucher.dto.response.VoucherResponse;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherUsage;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.repository.CampaignRepository;
import com.smartvoucher.repository.DistributionRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService;
    private final DistributionRepository distributionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview() {
        long totalVouchers = voucherRepository.count();
        long activeVouchers = voucherRepository.findByStatus(VoucherStatus.ACTIVE, PageRequest.of(0, 1))
                .getTotalElements();
        long totalUsages = voucherUsageRepository.count();
        long totalDistributions = distributionRepository.count();

        List<VoucherUsage> allUsages = voucherUsageRepository.findAll();
        BigDecimal totalDiscount = allUsages.stream()
                .map(VoucherUsage::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Top 5 vouchers by usage count
        List<VoucherResponse> topVouchers = voucherRepository.findAll().stream()
                .filter(v -> v.getCurrentUsageCount() > 0)
                .sorted(Comparator.comparingInt(Voucher::getCurrentUsageCount).reversed())
                .limit(5)
                .map(VoucherResponse::from)
                .toList();

        // Conversion rate: redeemed / distributed
        double conversionRate = totalDistributions > 0
                ? BigDecimal.valueOf(totalUsages)
                        .divide(BigDecimal.valueOf(totalDistributions), 4, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

        // Revenue by day (last 30 days)
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);
        List<VoucherUsage> recentUsages = voucherUsageRepository.findByUsedAtBetween(thirtyDaysAgo, now);
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, BigDecimal> revenueByDay = recentUsages.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getUsedAt().format(dayFormatter),
                        Collectors.reducing(BigDecimal.ZERO, VoucherUsage::getDiscountAmount, BigDecimal::add)
                ));

        // Active merchant count (ADMIN only)
        Long activeMerchantCount = null;
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userRepository.findByUsername(username).ifPresent(user -> {});
        var currentUserOpt = userRepository.findByUsername(username);
        if (currentUserOpt.isPresent() && currentUserOpt.get().getRole() == UserRole.ADMIN) {
            activeMerchantCount = voucherUsageRepository.countDistinctMerchantsWithUsage();
        }

        return DashboardOverviewResponse.builder()
                .totalVouchers(totalVouchers)
                .activeVouchers(activeVouchers)
                .totalUsages(totalUsages)
                .totalDiscountAmount(totalDiscount)
                .topVouchers(topVouchers)
                .conversionRate(conversionRate)
                .revenueByDay(revenueByDay)
                .activeMerchantCount(activeMerchantCount)
                .build();
    }

    @Transactional(readOnly = true)
    public CampaignStatsResponse getCampaignStats(Long campaignId) {
        return campaignService.getStats(campaignId);
    }

    @Transactional(readOnly = true)
    public List<UsageTrendResponse> getUsageTrend(OffsetDateTime from, OffsetDateTime to, String groupBy) {
        List<VoucherUsage> usages = voucherUsageRepository.findByUsedAtBetween(from, to);

        DateTimeFormatter formatter;
        switch (groupBy != null ? groupBy.toUpperCase() : "DAY") {
            case "MONTH" -> formatter = DateTimeFormatter.ofPattern("yyyy-MM");
            case "WEEK" -> formatter = DateTimeFormatter.ofPattern("yyyy-'W'ww");
            default -> formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        }

        Map<String, List<VoucherUsage>> grouped = usages.stream()
                .collect(Collectors.groupingBy(u -> u.getUsedAt().format(formatter)));

        return grouped.entrySet().stream()
                .map(entry -> new UsageTrendResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().map(VoucherUsage::getDiscountAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                ))
                .sorted(Comparator.comparing(UsageTrendResponse::getPeriod))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBranchStats(OffsetDateTime from, OffsetDateTime to) {
        List<VoucherUsage> usages = voucherUsageRepository.findByUsedAtBetween(from, to);

        Map<String, Long> branchCount = usages.stream()
                .filter(u -> u.getExternalBranchId() != null)
                .collect(Collectors.groupingBy(VoucherUsage::getExternalBranchId, Collectors.counting()));

        Map<String, BigDecimal> branchDiscount = usages.stream()
                .filter(u -> u.getExternalBranchId() != null)
                .collect(Collectors.groupingBy(
                        VoucherUsage::getExternalBranchId,
                        Collectors.reducing(BigDecimal.ZERO, VoucherUsage::getDiscountAmount, BigDecimal::add)
                ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("branchUsageCount", branchCount);
        result.put("branchDiscountAmount", branchDiscount);
        return result;
    }
}
