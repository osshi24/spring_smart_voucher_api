package com.smartvoucher.service;

import com.smartvoucher.dto.response.DashboardOverviewResponse;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private VoucherRepository voucherRepository;
    @Mock private VoucherUsageRepository voucherUsageRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignService campaignService;
    @Mock private DistributionRepository distributionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private User adminUser;
    private User merchantUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRole(UserRole.ADMIN);

        merchantUser = new User();
        merchantUser.setId(2L);
        merchantUser.setUsername("merchant");
        merchantUser.setRole(UserRole.USER);

        // Default stubs used by all tests
        when(voucherRepository.count()).thenReturn(100L);
        when(voucherRepository.findByStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(voucherUsageRepository.count()).thenReturn(50L);
        when(distributionRepository.count()).thenReturn(80L);
        when(voucherUsageRepository.findAll()).thenReturn(List.of());
        when(voucherRepository.findAll()).thenReturn(List.of());
        when(voucherUsageRepository.findByUsedAtBetween(any(), any())).thenReturn(List.of());
        when(voucherUsageRepository.countDistinctCustomers()).thenReturn(30L);
        when(voucherUsageRepository.sumDiscountAmountByPeriod(any(), any())).thenReturn(BigDecimal.ZERO);
        when(voucherUsageRepository.countByUsedAtBetween(any(), any())).thenReturn(0L);
        when(voucherUsageRepository.countDistinctCustomersByPeriod(any(), any())).thenReturn(0L);
    }

    private void mockSecurityContext(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // ── activeCustomerCount ─────────────────────────────────────────────────

    @Test
    void getOverview_returnsActiveCustomerCount() {
        mockSecurityContext("merchant");
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(merchantUser));
        when(voucherUsageRepository.countDistinctCustomers()).thenReturn(42L);

        DashboardOverviewResponse res = dashboardService.getOverview();

        assertThat(res.getActiveCustomerCount()).isEqualTo(42L);
    }

    // ── activeMerchantCount (ADMIN only) ────────────────────────────────────

    @Test
    void getOverview_adminUser_includesActiveMerchantCount() {
        mockSecurityContext("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(voucherUsageRepository.countDistinctMerchantsWithUsage()).thenReturn(7L);

        DashboardOverviewResponse res = dashboardService.getOverview();

        assertThat(res.getActiveMerchantCount()).isEqualTo(7L);
    }

    @Test
    void getOverview_nonAdminUser_activeMerchantCountIsNull() {
        mockSecurityContext("merchant");
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(merchantUser));

        DashboardOverviewResponse res = dashboardService.getOverview();

        assertThat(res.getActiveMerchantCount()).isNull();
    }

    // ── savingsGrowthRate ───────────────────────────────────────────────────

    @Test
    void getOverview_savingsGrowthRate_calculatedCorrectly() {
        mockSecurityContext("merchant");
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(merchantUser));

        // Previous month: 100, current month: 150 → growth = 50%
        when(voucherUsageRepository.sumDiscountAmountByPeriod(any(), any()))
                .thenReturn(BigDecimal.valueOf(100))   // first call = current
                .thenReturn(BigDecimal.valueOf(100));  // second call = prev (same stub reused)

        // Stub: current = 150, prev = 100
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime currentMonthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        when(voucherUsageRepository.sumDiscountAmountByPeriod(
                argThat(t -> t != null && !t.isBefore(currentMonthStart)), any()))
                .thenReturn(BigDecimal.valueOf(150));
        when(voucherUsageRepository.sumDiscountAmountByPeriod(
                argThat(t -> t != null && t.isBefore(currentMonthStart)), any()))
                .thenReturn(BigDecimal.valueOf(100));

        DashboardOverviewResponse res = dashboardService.getOverview();

        assertThat(res.getSavingsGrowthRate()).isNotNull();
        assertThat(res.getSavingsGrowthRate()).isEqualTo(50.0);
    }

    @Test
    void getOverview_savingsGrowthRate_nullWhenPrevIsZero() {
        mockSecurityContext("merchant");
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(merchantUser));
        when(voucherUsageRepository.sumDiscountAmountByPeriod(any(), any())).thenReturn(BigDecimal.ZERO);

        DashboardOverviewResponse res = dashboardService.getOverview();

        // prev = 0 → growth rate undefined → null
        assertThat(res.getSavingsGrowthRate()).isNull();
    }

    // ── activeUsersGrowthRate ───────────────────────────────────────────────

    @Test
    void getOverview_activeUsersGrowthRate_calculatedCorrectly() {
        mockSecurityContext("merchant");
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(merchantUser));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime currentMonthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        // current month: 20 users, prev month: 10 users → growth = 100%
        when(voucherUsageRepository.countDistinctCustomersByPeriod(
                argThat(t -> t != null && !t.isBefore(currentMonthStart)), any()))
                .thenReturn(20L);
        when(voucherUsageRepository.countDistinctCustomersByPeriod(
                argThat(t -> t != null && t.isBefore(currentMonthStart)), any()))
                .thenReturn(10L);

        DashboardOverviewResponse res = dashboardService.getOverview();

        assertThat(res.getActiveUsersGrowthRate()).isEqualTo(100.0);
    }

    // ── redemptionRateGrowth ────────────────────────────────────────────────

    @Test
    void getOverview_redemptionRateGrowth_calculatedCorrectly() {
        mockSecurityContext("merchant");
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(merchantUser));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime currentMonthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        // current month: 60 usages, prev month: 40 → growth = 50%
        when(voucherUsageRepository.countByUsedAtBetween(
                argThat(t -> t != null && !t.isBefore(currentMonthStart)), any()))
                .thenReturn(60L);
        when(voucherUsageRepository.countByUsedAtBetween(
                argThat(t -> t != null && t.isBefore(currentMonthStart)), any()))
                .thenReturn(40L);

        DashboardOverviewResponse res = dashboardService.getOverview();

        assertThat(res.getRedemptionRateGrowth()).isEqualTo(50.0);
    }

    // ── conversionRate ──────────────────────────────────────────────────────

    @Test
    void getOverview_conversionRate_calculatedCorrectly() {
        mockSecurityContext("merchant");
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(merchantUser));
        when(voucherUsageRepository.count()).thenReturn(40L);
        when(distributionRepository.count()).thenReturn(100L);

        DashboardOverviewResponse res = dashboardService.getOverview();

        assertThat(res.getConversionRate()).isEqualTo(0.4);
    }

    @Test
    void getOverview_conversionRate_zeroWhenNoDistributions() {
        mockSecurityContext("merchant");
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(merchantUser));
        when(distributionRepository.count()).thenReturn(0L);

        DashboardOverviewResponse res = dashboardService.getOverview();

        assertThat(res.getConversionRate()).isEqualTo(0.0);
    }

    // ── base fields ─────────────────────────────────────────────────────────

    @Test
    void getOverview_baseFields_returnedCorrectly() {
        mockSecurityContext("merchant");
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(merchantUser));
        when(voucherRepository.count()).thenReturn(200L);
        when(voucherUsageRepository.count()).thenReturn(75L);

        DashboardOverviewResponse res = dashboardService.getOverview();

        assertThat(res.getTotalVouchers()).isEqualTo(200L);
        assertThat(res.getTotalUsages()).isEqualTo(75L);
        assertThat(res.getTotalDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(res.getTopVouchers()).isEmpty();
    }
}
