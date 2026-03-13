package com.smartvoucher.service;

import com.smartvoucher.dto.request.VoucherCreateRequest;
import com.smartvoucher.dto.request.VoucherUpdateRequest;
import com.smartvoucher.dto.response.VoucherResponse;
import com.smartvoucher.entity.Campaign;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.exception.DuplicateResourceException;
import org.springframework.data.jpa.domain.Specification;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CampaignRepository;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final CustomerRepository customerRepository;
    private final VoucherCustomerRepository voucherCustomerRepository;

    @Transactional
    public VoucherResponse create(VoucherCreateRequest req) {
        if (voucherRepository.existsByCode(req.getCode())) {
            throw new DuplicateResourceException("Voucher code already exists: " + req.getCode());
        }

        validateDiscountValue(req.getDiscountType().name(), req.getDiscountValue());
        validateDateRange(req.getValidFrom(), req.getValidUntil());

        User currentUser = getCurrentUser();

        Voucher voucher = new Voucher();
        voucher.setCode(req.getCode().toUpperCase());
        voucher.setDescription(req.getDescription());
        voucher.setDiscountType(req.getDiscountType());
        voucher.setDiscountValue(req.getDiscountValue());
        voucher.setMaxDiscountAmount(req.getMaxDiscountAmount());
        voucher.setMinOrderValue(req.getMinOrderValue() != null ? req.getMinOrderValue() : BigDecimal.ZERO);
        voucher.setApplicableProducts(req.getApplicableProducts() != null ? req.getApplicableProducts() : new ArrayList<>());
        voucher.setApplicableCategories(req.getApplicableCategories() != null ? req.getApplicableCategories() : new ArrayList<>());
        voucher.setApplicableBranches(req.getApplicableBranches() != null ? req.getApplicableBranches() : new ArrayList<>());
        voucher.setMaxUsageTotal(req.getMaxUsageTotal());
        voucher.setMaxUsagePerCustomer(req.getMaxUsagePerCustomer());
        voucher.setIsPublic(req.getIsPublic() != null ? req.getIsPublic() : true);
        voucher.setValidFrom(req.getValidFrom());
        voucher.setValidUntil(req.getValidUntil());
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setCurrentUsageCount(0);
        voucher.setCreatedBy(currentUser);

        if (req.getCampaignId() != null) {
            Campaign campaign = campaignRepository.findById(req.getCampaignId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + req.getCampaignId()));
            voucher.setCampaign(campaign);
        }

        return VoucherResponse.from(voucherRepository.save(voucher));
    }

    @Transactional(readOnly = true)
    public VoucherResponse getById(Long id) {
        return VoucherResponse.from(findById(id));
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> getAll(Specification<Voucher> spec, Pageable pageable) {
        return voucherRepository.findAll(spec != null ? spec : Specification.where(null), pageable).map(VoucherResponse::from);
    }

    @Transactional
    public VoucherResponse update(Long id, VoucherUpdateRequest req) {
        Voucher voucher = findById(id);

        if (req.getDescription() != null) voucher.setDescription(req.getDescription());
        if (req.getDiscountType() != null) voucher.setDiscountType(req.getDiscountType());
        if (req.getDiscountValue() != null) {
            String type = req.getDiscountType() != null
                    ? req.getDiscountType().name()
                    : voucher.getDiscountType().name();
            validateDiscountValue(type, req.getDiscountValue());
            voucher.setDiscountValue(req.getDiscountValue());
        }
        if (req.getMaxDiscountAmount() != null) voucher.setMaxDiscountAmount(req.getMaxDiscountAmount());
        if (req.getMinOrderValue() != null) voucher.setMinOrderValue(req.getMinOrderValue());
        if (req.getApplicableProducts() != null) voucher.setApplicableProducts(req.getApplicableProducts());
        if (req.getApplicableCategories() != null) voucher.setApplicableCategories(req.getApplicableCategories());
        if (req.getApplicableBranches() != null) voucher.setApplicableBranches(req.getApplicableBranches());
        if (req.getMaxUsageTotal() != null) voucher.setMaxUsageTotal(req.getMaxUsageTotal());
        if (req.getMaxUsagePerCustomer() != null) voucher.setMaxUsagePerCustomer(req.getMaxUsagePerCustomer());
        if (req.getIsPublic() != null) voucher.setIsPublic(req.getIsPublic());
        if (req.getValidFrom() != null) voucher.setValidFrom(req.getValidFrom());
        if (req.getValidUntil() != null) voucher.setValidUntil(req.getValidUntil());
        if (req.getStatus() != null) voucher.setStatus(req.getStatus());

        if (req.getValidFrom() != null || req.getValidUntil() != null) {
            validateDateRange(voucher.getValidFrom(), voucher.getValidUntil());
        }

        return VoucherResponse.from(voucherRepository.save(voucher));
    }

    @Transactional
    public void assignCustomers(Long voucherId, java.util.List<Long> customerIds) {
        Voucher voucher = findById(voucherId);
        for (Long customerId : customerIds) {
            if (!voucherCustomerRepository.existsByVoucherIdAndCustomerId(voucherId, customerId)) {
                com.smartvoucher.entity.Customer customer = customerRepository.findById(customerId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
                com.smartvoucher.entity.VoucherCustomer vc = new com.smartvoucher.entity.VoucherCustomer();
                vc.setVoucher(voucher);
                vc.setCustomer(customer);
                voucherCustomerRepository.save(vc);
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        Voucher voucher = findById(id);
        if (voucher.getCurrentUsageCount() > 0) {
            throw new DuplicateResourceException("Cannot delete voucher that has been used " + voucher.getCurrentUsageCount() + " time(s)");
        }
        voucherRepository.delete(voucher);
    }

    private Voucher findById(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + id));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found: " + username));
    }

    private void validateDiscountValue(String discountType, BigDecimal value) {
        if ("PERCENTAGE".equals(discountType) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }
    }

    private void validateDateRange(java.time.OffsetDateTime from, java.time.OffsetDateTime until) {
        if (from != null && until != null && !until.isAfter(from)) {
            throw new IllegalArgumentException("Valid until must be after valid from");
        }
    }
}
