package com.smartvoucher.service;

import com.smartvoucher.dto.request.VoucherValidateRequest;
import com.smartvoucher.dto.response.VoucherValidateResponse;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherValidationServiceTest {

    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private VoucherCustomerRepository voucherCustomerRepository;
    @Mock
    private VoucherUsageRepository voucherUsageRepository;

    @InjectMocks
    private VoucherValidationService validationService;

    private Voucher voucher;
    private VoucherValidateRequest request;

    @BeforeEach
    void setUp() {
        voucher = new Voucher();
        voucher.setId(1L);
        voucher.setCode("VALID10");
        voucher.setDiscountType(DiscountType.PERCENTAGE);
        voucher.setDiscountValue(BigDecimal.TEN);
        voucher.setMinOrderValue(BigDecimal.valueOf(50000));
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setIsPublic(true);
        voucher.setCurrentUsageCount(0);
        voucher.setApplicableProducts(new ArrayList<>());
        voucher.setApplicableCategories(new ArrayList<>());
        voucher.setApplicableBranches(new ArrayList<>());
        voucher.setValidFrom(OffsetDateTime.now().minusDays(1));
        voucher.setValidUntil(OffsetDateTime.now().plusDays(30));

        request = new VoucherValidateRequest();
        request.setVoucherCode("VALID10");
        request.setCustomerId(1L);
        request.setOrderTotal(BigDecimal.valueOf(300000));
    }

    @Test
    void validate_validVoucher_returnsValid() {
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(voucher));

        VoucherValidateResponse response = validationService.validate(request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    @Test
    void validate_inactiveVoucher_returnsInvalid() {
        voucher.setStatus(VoucherStatus.INACTIVE);
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(voucher));

        VoucherValidateResponse response = validationService.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("not active");
    }

    @Test
    void validate_expiredVoucher_returnsInvalid() {
        voucher.setValidUntil(OffsetDateTime.now().minusDays(1));
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(voucher));

        VoucherValidateResponse response = validationService.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("expired");
    }

    @Test
    void validate_notYetValid_returnsInvalid() {
        voucher.setValidFrom(OffsetDateTime.now().plusDays(1));
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(voucher));

        VoucherValidateResponse response = validationService.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("not yet valid");
    }

    @Test
    void validate_orderBelowMinimum_returnsInvalid() {
        request.setOrderTotal(BigDecimal.valueOf(10000));
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(voucher));

        VoucherValidateResponse response = validationService.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("at least");
    }

    @Test
    void validate_maxUsageReached_returnsInvalid() {
        voucher.setMaxUsageTotal(10);
        voucher.setCurrentUsageCount(10);
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(voucher));

        VoucherValidateResponse response = validationService.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("limit");
    }

    @Test
    void validate_privateVoucherNotAssigned_returnsInvalid() {
        voucher.setIsPublic(false);
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(voucher));
        when(voucherCustomerRepository.existsByVoucherIdAndCustomerId(1L, 1L)).thenReturn(false);

        VoucherValidateResponse response = validationService.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("not assigned");
    }

    @Test
    void calculateDiscount_percentage_withCap() {
        voucher.setDiscountType(DiscountType.PERCENTAGE);
        voucher.setDiscountValue(BigDecimal.valueOf(20));
        voucher.setMaxDiscountAmount(BigDecimal.valueOf(50000));

        BigDecimal discount = validationService.calculateDiscount(voucher, BigDecimal.valueOf(500000));

        // 20% of 500000 = 100000, but capped at 50000
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    void calculateDiscount_fixedAmount() {
        voucher.setDiscountType(DiscountType.FIXED_AMOUNT);
        voucher.setDiscountValue(BigDecimal.valueOf(30000));

        BigDecimal discount = validationService.calculateDiscount(voucher, BigDecimal.valueOf(300000));

        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }
}
