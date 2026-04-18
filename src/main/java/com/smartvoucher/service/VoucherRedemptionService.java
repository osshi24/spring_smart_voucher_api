package com.smartvoucher.service;

import com.smartvoucher.annotation.Auditable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoucher.dto.request.VoucherRedeemRequest;
import com.smartvoucher.dto.response.RedemptionReverseResponse;
import com.smartvoucher.dto.response.VoucherValidateResponse;
import com.smartvoucher.entity.ApiKey;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherCode;
import com.smartvoucher.entity.VoucherUsage;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.event.VoucherRedeemedEvent;
import com.smartvoucher.exception.ConflictException;
import com.smartvoucher.exception.ForbiddenException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.exception.VoucherUsageLimitException;
import com.smartvoucher.repository.ApiKeyRepository;
import com.smartvoucher.repository.VoucherCodeRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherRedemptionService {

    private final VoucherRepository voucherRepository;
    private final VoucherCodeRepository voucherCodeRepository;
    private final VoucherCustomerRepository voucherCustomerRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final VoucherValidationService voucherValidationService;
    private final CustomerResolutionService customerResolutionService;
    private final ApiKeyRepository apiKeyRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;

    @Transactional
    public VoucherValidateResponse redeem(VoucherRedeemRequest req) {
        return redeem(req, null, null);
    }

    @Auditable(action = "REDEEM", entityType = "VoucherUsage")
    @Transactional
    public VoucherValidateResponse redeem(VoucherRedeemRequest req, String idempotencyKey, Long apiKeyId) {
        // --- Idempotency check ---
        String cacheKey = idempotencyKey != null ? buildIdempotencyKey(apiKeyId, idempotencyKey) : null;
        if (cacheKey != null) {
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    try {
                        VoucherValidateResponse resp = objectMapper.readValue(cached, VoucherValidateResponse.class);
                        resp.setIdempotent(true);
                        return resp;
                    } catch (Exception e) {
                        log.warn("Failed to deserialize idempotency cache: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Redis idempotency lookup failed, proceeding without cache: {}", e.getMessage());
            }
        }

        // --- Resolve customer ---
        User merchant = getMerchant(apiKeyId);
        Customer customer = customerResolutionService.resolve(
                req.getCustomerId(), req.getCustomerRef(), merchant, true);

        // --- Resolve voucher: try unique code first, then fall back to shared voucher code ---
        String inputCode = req.getVoucherCode().toUpperCase();
        VoucherCode uniqueCode = voucherCodeRepository.findByCodeIgnoreCase(inputCode).orElse(null);
        Voucher voucherCheck;
        if (uniqueCode != null) {
            voucherCheck = uniqueCode.getVoucher();
        } else {
            voucherCheck = voucherRepository.findByCode(inputCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + req.getVoucherCode()));
            if (voucherCheck.getCodeType() == com.smartvoucher.entity.enums.CodeType.UNIQUE) {
                throw new IllegalArgumentException(
                        "Voucher này yêu cầu sử dụng mã code riêng của bạn, không dùng được master code");
            }
        }

        // --- Idempotency check before used-flag check so repeated same-order calls return idempotent ---
        if (voucherUsageRepository.existsByVoucherIdAndExternalOrderId(
                voucherCheck.getId(), req.getExternalOrderId())) {
            String idemCode = uniqueCode != null ? uniqueCode.getCode() : voucherCheck.getCode();
            VoucherValidateResponse resp = VoucherValidateResponse.redeemed(
                    idemCode, voucherCheck.getDiscountType(),
                    voucherCheck.getDiscountValue(), BigDecimal.ZERO, voucherCheck.getMaxDiscountAmount());
            resp.setIdempotent(true);
            return resp;
        }

        // --- Validate unique code ownership/used status ---
        if (uniqueCode != null) {
            if (Boolean.TRUE.equals(uniqueCode.getUsed())) {
                throw new ConflictException("Voucher đã được sử dụng");
            }
            if (uniqueCode.getCustomer() != null
                    && !uniqueCode.getCustomer().getId().equals(customer.getId())) {
                throw new ForbiddenException("Voucher này không thuộc về khách hàng");
            }
        }

        // --- Pessimistic lock ---
        entityManager.detach(voucherCheck);
        Voucher voucher = voucherRepository.findByIdWithLock(voucherCheck.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found"));

        // --- Validation with lock held ---
        if (voucher.getStatus() == VoucherStatus.PAUSED) {
            throw new IllegalArgumentException("Voucher đang tạm ngưng");
        }
        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new IllegalArgumentException("Voucher is not active");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(voucher.getValidFrom()) || now.isAfter(voucher.getValidUntil())) {
            throw new IllegalArgumentException("Voucher is not valid at this time");
        }

        if (voucher.getMaxUsageTotal() != null &&
                voucher.getCurrentUsageCount() >= voucher.getMaxUsageTotal()) {
            throw new VoucherUsageLimitException("Voucher usage limit has been reached");
        }

        if (req.getOrderTotal().compareTo(voucher.getMinOrderValue()) < 0) {
            throw new IllegalArgumentException("Order total does not meet minimum requirement");
        }

        // --- Check private voucher assignment (SHARED type only; UNIQUE type already checked via uniqueCode.customer) ---
        if (!voucher.getIsPublic() && uniqueCode == null) {
            boolean assigned = voucherCustomerRepository.existsByVoucherIdAndCustomerId(voucher.getId(), customer.getId());
            if (!assigned) {
                throw new ForbiddenException("Voucher is not assigned to this customer");
            }
        }

        // --- Calculate discount ---
        BigDecimal discountAmount = voucherValidationService.calculateDiscount(voucher, req.getOrderTotal());

        // --- Save usage ---
        VoucherUsage usage = new VoucherUsage();
        usage.setVoucher(voucher);
        usage.setCustomer(customer);
        usage.setExternalOrderId(req.getExternalOrderId());
        usage.setExternalBranchId(req.getExternalBranchId());
        usage.setDiscountAmount(discountAmount);
        usage.setOrderTotal(req.getOrderTotal());
        voucherUsageRepository.save(usage);

        // --- Update usage count + auto-transition ---
        voucher.setCurrentUsageCount(voucher.getCurrentUsageCount() + 1);
        if (voucher.getMaxUsageTotal() != null &&
                voucher.getCurrentUsageCount() >= voucher.getMaxUsageTotal()) {
            voucher.setStatus(VoucherStatus.FULLY_USED);
        }
        voucherRepository.save(voucher);

        // --- Mark unique code as used (if applicable) ---
        if (uniqueCode != null) {
            uniqueCode.setUsed(true);
            uniqueCode.setUsedAt(OffsetDateTime.now());
            if (uniqueCode.getCustomer() == null) {
                uniqueCode.setCustomer(customer);
            }
            voucherCodeRepository.save(uniqueCode);
        }

        // --- Dispatch webhook ---
        if (apiKeyId != null) {
            try {
                User merchantUser = getMerchant(apiKeyId);
                if (merchantUser != null) {
                    webhookService.dispatchRedemptionEvent(merchantUser.getId(), usage);
                }
            } catch (Exception e) {
                log.warn("Webhook dispatch failed: {}", e.getMessage());
            }
        }

        // --- Publish redemption event ---
        eventPublisher.publishEvent(new VoucherRedeemedEvent(
                this,
                customer.getId(),
                customer.getEmail(),
                voucher.getCode(),
                voucher.getDiscountValue(),
                voucher.getDiscountType(),
                voucher.getValidUntil()
        ));

        String responseCode = uniqueCode != null ? uniqueCode.getCode() : voucher.getCode();
        VoucherValidateResponse response = VoucherValidateResponse.redeemed(
                responseCode,
                voucher.getDiscountType(),
                voucher.getDiscountValue(),
                discountAmount,
                voucher.getMaxDiscountAmount()
        );

        // --- Store idempotency result ---
        if (cacheKey != null) {
            try {
                redisTemplate.opsForValue().set(
                        cacheKey,
                        objectMapper.writeValueAsString(response),
                        Duration.ofHours(24)
                );
            } catch (Exception e) {
                log.warn("Failed to cache idempotency result: {}", e.getMessage());
            }
        }

        return response;
    }

    @Auditable(action = "REVERSE", entityType = "VoucherUsage", entityIdSpel = "#usageId")
    @Transactional
    public RedemptionReverseResponse reverse(Long usageId, Long apiKeyId) {
        VoucherUsage usage = voucherUsageRepository.findById(usageId)
                .orElseThrow(() -> new ResourceNotFoundException("Redemption not found: " + usageId));

        // Ownership check: API key merchant must own the voucher
        if (apiKeyId != null) {
            User merchant = getMerchant(apiKeyId);
            if (merchant != null && !usage.getVoucher().getCreatedBy().getId().equals(merchant.getId())) {
                throw new com.smartvoucher.exception.ForbiddenException("You do not have permission to reverse this redemption");
            }
        }

        Voucher voucher = voucherRepository.findByIdWithLock(usage.getVoucher().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found"));

        voucherUsageRepository.delete(usage);

        int newCount = Math.max(0, voucher.getCurrentUsageCount() - 1);
        voucher.setCurrentUsageCount(newCount);

        if (voucher.getStatus() == VoucherStatus.FULLY_USED
                && (voucher.getMaxUsageTotal() == null || newCount < voucher.getMaxUsageTotal())) {
            voucher.setStatus(VoucherStatus.ACTIVE);
        }
        voucherRepository.save(voucher);

        return RedemptionReverseResponse.builder()
                .usageId(usageId)
                .voucherCode(voucher.getCode())
                .reversed(true)
                .newUsageCount(newCount)
                .build();
    }

    private String buildIdempotencyKey(Long apiKeyId, String key) {
        return "idempotency:" + (apiKeyId != null ? apiKeyId : "anon") + ":" + key;
    }

    private User getMerchant(Long apiKeyId) {
        if (apiKeyId == null) return null;
        return apiKeyRepository.findById(apiKeyId)
                .map(ApiKey::getCreatedBy)
                .orElse(null);
    }
}
