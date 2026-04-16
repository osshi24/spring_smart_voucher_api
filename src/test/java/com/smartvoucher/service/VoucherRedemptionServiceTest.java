package com.smartvoucher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoucher.dto.request.VoucherRedeemRequest;
import com.smartvoucher.dto.response.VoucherValidateResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherCode;
import com.smartvoucher.entity.enums.CodeType;
import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.exception.ConflictException;
import com.smartvoucher.exception.ForbiddenException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.exception.VoucherUsageLimitException;
import com.smartvoucher.repository.ApiKeyRepository;
import com.smartvoucher.repository.VoucherCodeRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherRedemptionServiceTest {

    @Mock private VoucherRepository voucherRepository;
    @Mock private VoucherCodeRepository voucherCodeRepository;
    @Mock private VoucherUsageRepository voucherUsageRepository;
    @Mock private VoucherValidationService voucherValidationService;
    @Mock private CustomerResolutionService customerResolutionService;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private EntityManager entityManager;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ObjectMapper objectMapper;
    @Mock private WebhookService webhookService;

    @InjectMocks
    private VoucherRedemptionService redemptionService;

    private Voucher voucher;
    private Customer customer;
    private VoucherRedeemRequest request;

    @BeforeEach
    void setUp() {
        voucher = new Voucher();
        voucher.setId(1L);
        voucher.setCode("OSHI");
        voucher.setDiscountType(DiscountType.PERCENTAGE);
        voucher.setDiscountValue(new BigDecimal("10"));
        voucher.setMinOrderValue(new BigDecimal("50000"));
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setIsPublic(true);
        voucher.setCodeType(CodeType.SHARED);
        voucher.setCurrentUsageCount(0);
        voucher.setApplicableProducts(new ArrayList<>());
        voucher.setApplicableCategories(new ArrayList<>());
        voucher.setApplicableBranches(new ArrayList<>());
        voucher.setValidFrom(OffsetDateTime.now().minusDays(1));
        voucher.setValidUntil(OffsetDateTime.now().plusDays(30));

        customer = new Customer();
        customer.setId(42L);
        customer.setExternalId("CUS-42");
        customer.setFullName("Test Customer");

        request = new VoucherRedeemRequest();
        request.setVoucherCode("OSHI");
        request.setCustomerRef("42");
        request.setExternalOrderId("INV-001");
        request.setOrderTotal(new BigDecimal("100000"));
    }

    // -------------------- SHARED VOUCHER --------------------

    @Nested
    @DisplayName("SHARED voucher")
    class SharedVoucherTests {

        @Test
        @DisplayName("Success: 10% of 100,000 = 10,000 discount")
        void redeem_sharedVoucher_success() {
            when(voucherCodeRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(voucherRepository.findByCode("OSHI")).thenReturn(Optional.of(voucher));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(false);
            when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));
            when(voucherValidationService.calculateDiscount(eq(voucher), eq(new BigDecimal("100000"))))
                    .thenReturn(new BigDecimal("10000"));

            VoucherValidateResponse resp = redemptionService.redeem(request);

            assertThat(resp.isValid()).isTrue();
            assertThat(resp.getVoucherCode()).isEqualTo("OSHI");
            assertThat(resp.getDiscountAmount()).isEqualByComparingTo("10000");
            assertThat(resp.getMessage()).contains("redeemed");
            verify(voucherUsageRepository).save(any());
            verify(voucherRepository).save(voucher);
        }

        @Test
        @DisplayName("Duplicate externalOrderId returns idempotent success (not throw)")
        void redeem_duplicateOrder_returnsIdempotent() {
            when(voucherCodeRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(voucherRepository.findByCode("OSHI")).thenReturn(Optional.of(voucher));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(true);

            VoucherValidateResponse resp = redemptionService.redeem(request);

            assertThat(resp.isValid()).isTrue();
            assertThat(resp.getIdempotent()).isTrue();
            verify(voucherUsageRepository, never()).save(any());
            verify(voucherRepository, never()).save(any(Voucher.class));
        }

        @Test
        @DisplayName("Voucher not found → ResourceNotFoundException")
        void redeem_voucherNotFound_throws() {
            when(voucherCodeRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(voucherRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);

            assertThatThrownBy(() -> redemptionService.redeem(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Voucher not found");
        }

        @Test
        @DisplayName("Inactive voucher → IllegalArgumentException")
        void redeem_inactiveVoucher_throws() {
            voucher.setStatus(VoucherStatus.PAUSED);
            when(voucherCodeRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(voucherRepository.findByCode("OSHI")).thenReturn(Optional.of(voucher));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(false);
            when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));

            assertThatThrownBy(() -> redemptionService.redeem(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tạm ngưng");
        }

        @Test
        @DisplayName("Order total below minOrderValue → throws")
        void redeem_belowMinOrderValue_throws() {
            voucher.setMinOrderValue(new BigDecimal("500000"));
            when(voucherCodeRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(voucherRepository.findByCode("OSHI")).thenReturn(Optional.of(voucher));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(false);
            when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));

            assertThatThrownBy(() -> redemptionService.redeem(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minimum");
        }

        @Test
        @DisplayName("maxUsageTotal reached → VoucherUsageLimitException")
        void redeem_maxUsageReached_throws() {
            voucher.setMaxUsageTotal(5);
            voucher.setCurrentUsageCount(5);
            when(voucherCodeRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(voucherRepository.findByCode("OSHI")).thenReturn(Optional.of(voucher));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(false);
            when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));

            assertThatThrownBy(() -> redemptionService.redeem(request))
                    .isInstanceOf(VoucherUsageLimitException.class);
        }

        @Test
        @DisplayName("Auto-transition to FULLY_USED when last usage reached")
        void redeem_autoTransitionFullyUsed() {
            voucher.setMaxUsageTotal(1);
            voucher.setCurrentUsageCount(0);
            when(voucherCodeRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(voucherRepository.findByCode("OSHI")).thenReturn(Optional.of(voucher));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(false);
            when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));
            when(voucherValidationService.calculateDiscount(any(), any())).thenReturn(new BigDecimal("10000"));

            redemptionService.redeem(request);

            assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.FULLY_USED);
            assertThat(voucher.getCurrentUsageCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Expired voucher → throws")
        void redeem_expiredVoucher_throws() {
            voucher.setValidUntil(OffsetDateTime.now().minusDays(1));
            when(voucherCodeRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(voucherRepository.findByCode("OSHI")).thenReturn(Optional.of(voucher));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(false);
            when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));

            assertThatThrownBy(() -> redemptionService.redeem(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not valid at this time");
        }
    }

    // -------------------- UNIQUE VOUCHER --------------------

    @Nested
    @DisplayName("UNIQUE voucher")
    class UniqueVoucherTests {

        private VoucherCode uniqueCode;

        @BeforeEach
        void setupUnique() {
            voucher.setCodeType(CodeType.UNIQUE);
            uniqueCode = new VoucherCode();
            uniqueCode.setId(100L);
            uniqueCode.setCode("ABCD1234");
            uniqueCode.setVoucher(voucher);
            uniqueCode.setCustomer(customer);
            uniqueCode.setUsed(false);

            request.setVoucherCode("ABCD1234");
        }

        @Test
        @DisplayName("Success: redeem unique code, mark used=true, response has unique code")
        void redeem_uniqueCode_success() {
            when(voucherCodeRepository.findByCode("ABCD1234")).thenReturn(Optional.of(uniqueCode));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(false);
            when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));
            when(voucherValidationService.calculateDiscount(any(), any())).thenReturn(new BigDecimal("10000"));

            VoucherValidateResponse resp = redemptionService.redeem(request);

            assertThat(resp.isValid()).isTrue();
            assertThat(resp.getVoucherCode()).isEqualTo("ABCD1234");
            assertThat(uniqueCode.getUsed()).isTrue();
            assertThat(uniqueCode.getUsedAt()).isNotNull();
            verify(voucherCodeRepository).save(uniqueCode);
        }

        @Test
        @DisplayName("Used unique code → ConflictException")
        void redeem_usedUniqueCode_throws() {
            uniqueCode.setUsed(true);
            when(voucherCodeRepository.findByCode("ABCD1234")).thenReturn(Optional.of(uniqueCode));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);

            assertThatThrownBy(() -> redemptionService.redeem(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("đã được sử dụng");
        }

        @Test
        @DisplayName("Unique code belongs to another customer → ForbiddenException")
        void redeem_wrongCustomer_throws() {
            Customer other = new Customer();
            other.setId(999L);
            uniqueCode.setCustomer(other);
            when(voucherCodeRepository.findByCode("ABCD1234")).thenReturn(Optional.of(uniqueCode));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);

            assertThatThrownBy(() -> redemptionService.redeem(request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("không thuộc về khách hàng");
        }

        @Test
        @DisplayName("Unassigned unique code auto-binds to redeeming customer")
        void redeem_unassignedUniqueCode_autoBindsCustomer() {
            uniqueCode.setCustomer(null);
            when(voucherCodeRepository.findByCode("ABCD1234")).thenReturn(Optional.of(uniqueCode));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(false);
            when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));
            when(voucherValidationService.calculateDiscount(any(), any())).thenReturn(new BigDecimal("10000"));

            redemptionService.redeem(request);

            assertThat(uniqueCode.getCustomer()).isEqualTo(customer);
            assertThat(uniqueCode.getUsed()).isTrue();
        }
    }

    // -------------------- IDEMPOTENCY --------------------

    @Nested
    @DisplayName("Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("Redis cache hit returns cached response immediately")
        void redeem_redisCacheHit_returnsCached() throws Exception {
            String cacheKey = "idempotency:10:my-key";
            String cachedJson = "{\"valid\":true,\"voucherCode\":\"OSHI\",\"discountAmount\":10000}";
            VoucherValidateResponse cached = new VoucherValidateResponse();
            cached.setValid(true);
            cached.setVoucherCode("OSHI");
            cached.setDiscountAmount(new BigDecimal("10000"));

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(cacheKey)).thenReturn(cachedJson);
            when(objectMapper.readValue(cachedJson, VoucherValidateResponse.class)).thenReturn(cached);

            VoucherValidateResponse resp = redemptionService.redeem(request, "my-key", 10L);

            assertThat(resp.getIdempotent()).isTrue();
            assertThat(resp.getDiscountAmount()).isEqualByComparingTo("10000");
            verify(voucherRepository, never()).findByCode(any());
        }

        @Test
        @DisplayName("Redis down → fallback to normal flow, no exception")
        void redeem_redisDown_fallsBackGracefully() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis down"));
            when(voucherCodeRepository.findByCode("OSHI")).thenReturn(Optional.empty());
            when(voucherRepository.findByCode("OSHI")).thenReturn(Optional.of(voucher));
            when(customerResolutionService.resolve(isNull(), eq("42"), any(), eq(true))).thenReturn(customer);
            when(voucherUsageRepository.existsByVoucherIdAndExternalOrderId(1L, "INV-001")).thenReturn(false);
            when(voucherRepository.findByIdWithLock(1L)).thenReturn(Optional.of(voucher));
            when(voucherValidationService.calculateDiscount(any(), any())).thenReturn(new BigDecimal("10000"));

            VoucherValidateResponse resp = redemptionService.redeem(request, "my-key", 10L);

            assertThat(resp.isValid()).isTrue();
            assertThat(resp.getDiscountAmount()).isEqualByComparingTo("10000");
        }
    }
}
