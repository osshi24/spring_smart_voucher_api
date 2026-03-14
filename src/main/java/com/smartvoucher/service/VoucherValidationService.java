package com.smartvoucher.service;

import com.smartvoucher.dto.request.VoucherValidateRequest;
import com.smartvoucher.dto.response.VoucherValidateResponse;
import com.smartvoucher.entity.ApiKey;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.ApiKeyRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoucherValidationService {

    private final VoucherRepository voucherRepository;
    private final VoucherCustomerRepository voucherCustomerRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final CustomerResolutionService customerResolutionService;
    private final ApiKeyRepository apiKeyRepository;

    @Transactional(readOnly = true)
    public VoucherValidateResponse validate(VoucherValidateRequest req) {
        return validate(req, null);
    }

    @Transactional
    public VoucherValidateResponse validate(VoucherValidateRequest req, Long apiKeyId) {
        Voucher voucher = voucherRepository.findByCode(req.getVoucherCode().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + req.getVoucherCode()));

        // Check status
        if (voucher.getStatus() == VoucherStatus.PAUSED) {
            return VoucherValidateResponse.invalid("Voucher đang tạm ngưng");
        }
        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            return VoucherValidateResponse.invalid("Voucher is not active");
        }

        // Check validity period
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(voucher.getValidFrom())) {
            return VoucherValidateResponse.invalid("Voucher is not yet valid");
        }
        if (now.isAfter(voucher.getValidUntil())) {
            return VoucherValidateResponse.invalid("Voucher has expired");
        }

        // Check minimum order value
        if (req.getOrderTotal().compareTo(voucher.getMinOrderValue()) < 0) {
            return VoucherValidateResponse.invalid(
                    "Order total must be at least " + voucher.getMinOrderValue()
            );
        }

        // Check applicable products (empty list = all products)
        if (isNotEmpty(voucher.getApplicableProducts()) && isNotEmpty(req.getProducts())) {
            boolean hasApplicableProduct = req.getProducts().stream()
                    .anyMatch(p -> voucher.getApplicableProducts().contains(p));
            if (!hasApplicableProduct) {
                return VoucherValidateResponse.invalid("Voucher is not applicable for these products");
            }
        }

        // Check applicable categories
        if (isNotEmpty(voucher.getApplicableCategories()) && req.getCategoryId() != null) {
            if (!voucher.getApplicableCategories().contains(req.getCategoryId())) {
                return VoucherValidateResponse.invalid("Voucher is not applicable for this category");
            }
        }

        // Check applicable branches
        if (isNotEmpty(voucher.getApplicableBranches()) && req.getBranchId() != null) {
            if (!voucher.getApplicableBranches().contains(req.getBranchId())) {
                return VoucherValidateResponse.invalid("Voucher is not applicable for this branch");
            }
        }

        // Resolve customer via customerRef or customerId
        User merchant = getMerchant(apiKeyId);
        Customer customer = customerResolutionService.resolve(
                req.getCustomerId(), req.getCustomerRef(), merchant, true);

        // Check public vs private
        if (!voucher.getIsPublic()) {
            boolean customerAssigned = voucherCustomerRepository.existsByVoucherIdAndCustomerId(
                    voucher.getId(), customer.getId()
            );
            if (!customerAssigned) {
                return VoucherValidateResponse.invalid("Voucher is not assigned to this customer");
            }
        }

        // Check max usage total
        if (voucher.getMaxUsageTotal() != null &&
                voucher.getCurrentUsageCount() >= voucher.getMaxUsageTotal()) {
            return VoucherValidateResponse.invalid("Voucher usage limit reached");
        }

        // Check max usage per customer
        if (voucher.getMaxUsagePerCustomer() != null) {
            long customerUsageCount = voucherUsageRepository.countByVoucherIdAndCustomerId(
                    voucher.getId(), customer.getId()
            );
            if (customerUsageCount >= voucher.getMaxUsagePerCustomer()) {
                return VoucherValidateResponse.invalid("Customer has reached usage limit for this voucher");
            }
        }

        // Check unique code assignment
        if (voucher.getCodeType() != null && voucher.getCodeType() == com.smartvoucher.entity.enums.CodeType.UNIQUE) {
            boolean assigned = voucherCustomerRepository.existsByVoucherIdAndCustomerId(voucher.getId(), customer.getId());
            if (!assigned) {
                return VoucherValidateResponse.invalid("Voucher unique code not assigned to this customer");
            }
        }

        BigDecimal discountAmount = calculateDiscount(voucher, req.getOrderTotal());

        return VoucherValidateResponse.valid(
                voucher.getCode(),
                voucher.getDiscountType(),
                voucher.getDiscountValue(),
                discountAmount,
                voucher.getMaxDiscountAmount()
        );
    }

    public BigDecimal calculateDiscount(Voucher voucher, BigDecimal orderTotal) {
        BigDecimal discount;
        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = orderTotal.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null &&
                    discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discount = voucher.getMaxDiscountAmount();
            }
        } else {
            discount = voucher.getDiscountValue();
            if (discount.compareTo(orderTotal) > 0) {
                discount = orderTotal;
            }
        }
        return discount;
    }

    private User getMerchant(Long apiKeyId) {
        if (apiKeyId == null) return null;
        return apiKeyRepository.findById(apiKeyId)
                .map(ApiKey::getCreatedBy)
                .orElse(null);
    }

    private boolean isNotEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }
}
