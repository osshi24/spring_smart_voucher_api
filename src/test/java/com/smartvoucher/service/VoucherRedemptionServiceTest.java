package com.smartvoucher.service;

import com.smartvoucher.dto.request.VoucherRedeemRequest;
import com.smartvoucher.dto.response.VoucherValidateResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.VoucherUsageLimitException;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherRedemptionServiceTest {

    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private VoucherCustomerRepository voucherCustomerRepository;
    @Mock
    private VoucherUsageRepository voucherUsageRepository;
    @Mock
    private VoucherValidationService voucherValidationService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private VoucherRedemptionService redemptionService;

    private Voucher voucher;
    private Customer customer;
    private VoucherRedeemRequest request;

    @BeforeEach
    void setUp() {
        voucher = new Voucher();
        voucher.setId(1L);
        voucher.setCode("SAVE10");
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

        customer = new Customer();
        customer.setId(1L);
        customer.setExternalId("CUS001");
        customer.setFullName("Test Customer");

        request = new VoucherRedeemRequest();
        request.setVoucherCode("SAVE10");
        request.setCustomerId(1L);
        request.setExternalOrderId("ORD001");
        request.setOrderTotal(BigDecimal.valueOf(200000));
    }

    @Test
    void redeem_success_returnsValidResponse() {
        when(voucherRepository.findByCode("SAVE10")).thenReturn(Optional.of(voucher));
        when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "ORD001")).thenReturn(false);
        when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(voucherValidationService.calculateDiscount(eq(voucher), eq(BigDecimal.valueOf(200000))))
                .thenReturn(BigDecimal.valueOf(20000));
        when(voucherRepository.save(any())).thenReturn(voucher);

        VoucherValidateResponse response = redemptionService.redeem(request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        verify(voucherUsageRepository).save(any());
        verify(voucherRepository).save(voucher);
    }

    @Test
    void redeem_duplicateOrder_throws() {
        when(voucherRepository.findByCode("SAVE10")).thenReturn(Optional.of(voucher));
        when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "ORD001")).thenReturn(true);

        assertThatThrownBy(() -> redemptionService.redeem(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ORD001");
    }

    @Test
    void redeem_maxUsageReached_throws() {
        voucher.setMaxUsageTotal(5);
        voucher.setCurrentUsageCount(5);
        when(voucherRepository.findByCode("SAVE10")).thenReturn(Optional.of(voucher));
        when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "ORD001")).thenReturn(false);
        when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));

        assertThatThrownBy(() -> redemptionService.redeem(request))
                .isInstanceOf(VoucherUsageLimitException.class);
    }

    @Test
    void redeem_autoMarksFullyUsed_whenLimitReached() {
        voucher.setMaxUsageTotal(1);
        voucher.setCurrentUsageCount(0);
        when(voucherRepository.findByCode("SAVE10")).thenReturn(Optional.of(voucher));
        when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "ORD001")).thenReturn(false);
        when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(voucherValidationService.calculateDiscount(any(), any())).thenReturn(BigDecimal.valueOf(20000));
        when(voucherRepository.save(any())).thenReturn(voucher);

        redemptionService.redeem(request);

        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.FULLY_USED);
        assertThat(voucher.getCurrentUsageCount()).isEqualTo(1);
    }

    @Test
    void redeem_inactiveVoucher_throws() {
        voucher.setStatus(VoucherStatus.INACTIVE);
        when(voucherRepository.findByCode("SAVE10")).thenReturn(Optional.of(voucher));
        when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "ORD001")).thenReturn(false);
        when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));

        assertThatThrownBy(() -> redemptionService.redeem(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }
}
