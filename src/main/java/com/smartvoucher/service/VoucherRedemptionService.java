package com.smartvoucher.service;

import com.smartvoucher.dto.request.VoucherRedeemRequest;
import com.smartvoucher.dto.request.VoucherValidateRequest;
import com.smartvoucher.dto.response.VoucherValidateResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherUsage;
import com.smartvoucher.event.VoucherRedeemedEvent;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.exception.VoucherUsageLimitException;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class VoucherRedemptionService {

    private final VoucherRepository voucherRepository;
    private final CustomerRepository customerRepository;
    private final VoucherCustomerRepository voucherCustomerRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final VoucherValidationService voucherValidationService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public VoucherValidateResponse redeem(VoucherRedeemRequest req) {
        // Check for duplicate order (idempotency)
        Voucher voucherCheck = voucherRepository.findByCode(req.getVoucherCode().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + req.getVoucherCode()));

        if (voucherUsageRepository.existsByVoucherIdAndExternalOrderId(
                voucherCheck.getId(), req.getExternalOrderId())) {
            throw new DuplicateResourceException("Voucher already used for order: " + req.getExternalOrderId());
        }

        // Pessimistic lock on voucher - evict from L1 cache first to ensure fresh read
        entityManager.detach(voucherCheck);
        Voucher voucher = voucherRepository.findByIdWithLock(voucherCheck.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found"));

        // Double-check validation with lock held
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

        if (voucher.getMaxUsagePerCustomer() != null) {
            long customerUsageCount = voucherUsageRepository.countByVoucherIdAndCustomerId(
                    voucher.getId(), req.getCustomerId()
            );
            if (customerUsageCount >= voucher.getMaxUsagePerCustomer()) {
                throw new VoucherUsageLimitException("Customer has reached usage limit for this voucher");
            }
        }

        // Check order total vs min order value
        if (req.getOrderTotal().compareTo(voucher.getMinOrderValue()) < 0) {
            throw new IllegalArgumentException("Order total does not meet minimum requirement");
        }

        // Get customer
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + req.getCustomerId()));

        // Calculate discount
        BigDecimal discountAmount = voucherValidationService.calculateDiscount(voucher, req.getOrderTotal());

        // Save usage record
        VoucherUsage usage = new VoucherUsage();
        usage.setVoucher(voucher);
        usage.setCustomer(customer);
        usage.setExternalOrderId(req.getExternalOrderId());
        usage.setExternalBranchId(req.getExternalBranchId());
        usage.setDiscountAmount(discountAmount);
        usage.setOrderTotal(req.getOrderTotal());
        voucherUsageRepository.save(usage);

        // Update usage count
        voucher.setCurrentUsageCount(voucher.getCurrentUsageCount() + 1);

        // Auto-update status if fully used
        if (voucher.getMaxUsageTotal() != null &&
                voucher.getCurrentUsageCount() >= voucher.getMaxUsageTotal()) {
            voucher.setStatus(VoucherStatus.FULLY_USED);
        }
        voucherRepository.save(voucher);

        // Publish event to send confirmation email asynchronously
        eventPublisher.publishEvent(new VoucherRedeemedEvent(
                this,
                customer.getId(),
                customer.getEmail(),
                voucher.getCode(),
                voucher.getDiscountValue(),
                voucher.getDiscountType(),
                voucher.getValidUntil()
        ));

        return VoucherValidateResponse.valid(
                voucher.getCode(),
                voucher.getDiscountType(),
                voucher.getDiscountValue(),
                discountAmount,
                voucher.getMaxDiscountAmount()
        );
    }
}
