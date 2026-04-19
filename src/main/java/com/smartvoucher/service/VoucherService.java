package com.smartvoucher.service;

import com.smartvoucher.annotation.Auditable;
import com.smartvoucher.dto.request.BulkAssignRequest;
import com.smartvoucher.dto.request.VoucherCreateRequest;
import com.smartvoucher.dto.request.VoucherUpdateRequest;
import com.smartvoucher.dto.response.BulkDistributeResponse;
import com.smartvoucher.dto.response.BulkOperationResponse;
import com.smartvoucher.dto.response.VoucherResponse;
import com.smartvoucher.entity.Campaign;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherCustomer;
import com.smartvoucher.entity.VoucherDistribution;
import com.smartvoucher.entity.enums.CampaignStatus;
import com.smartvoucher.entity.enums.DistributionChannel;
import com.smartvoucher.entity.enums.DistributionStatus;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.entity.enums.VoucherStatus;
import java.util.List;
import com.smartvoucher.exception.ConflictException;
import com.smartvoucher.exception.DuplicateResourceException;
import org.springframework.data.jpa.domain.Specification;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CampaignRepository;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.DistributionRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
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
    private final VoucherUsageRepository voucherUsageRepository;
    private final DistributionProcessor distributionProcessor;
    private final DistributionRepository distributionRepository;

    @Auditable(action = "CREATE", entityType = "Voucher", entityIdSpel = "#result?.id")
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
        voucher.setMaxUsagePerCustomer(req.getMaxUsagePerCustomer());
        voucher.setIsPublic(req.getIsPublic() != null ? req.getIsPublic() : true);
        voucher.setCodeType(req.getCodeType() != null ? req.getCodeType() : com.smartvoucher.entity.enums.CodeType.SHARED);
        // For UNIQUE vouchers, maxUsageTotal is derived from the number of generated codes (auto-synced via VoucherCodeService)
        voucher.setMaxUsageTotal(voucher.getCodeType() == com.smartvoucher.entity.enums.CodeType.UNIQUE
                ? Integer.valueOf(0)
                : req.getMaxUsageTotal());
        voucher.setValidFrom(req.getValidFrom());
        voucher.setValidUntil(req.getValidUntil());
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setCurrentUsageCount(0);
        voucher.setCreatedBy(currentUser);

        if (req.getCampaignId() != null) {
            Campaign campaign = campaignRepository.findById(req.getCampaignId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + req.getCampaignId()));
            if (campaign.getStatus() == CampaignStatus.ENDED) {
                throw new IllegalArgumentException("Cannot add voucher to an ENDED campaign");
            }
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
        return voucherRepository.findAll(withOwnerFilter(spec), pageable).map(VoucherResponse::from);
    }

    @Auditable(action = "UPDATE", entityType = "Voucher", entityIdSpel = "#id")
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

    @Auditable(action = "ASSIGN_CUSTOMERS", entityType = "Voucher", entityIdSpel = "#voucherId")
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

    @Auditable(action = "DELETE", entityType = "Voucher", entityIdSpel = "#id")
    @Transactional
    public void delete(Long id) {
        Voucher voucher = findById(id);
        long usageCount = voucherUsageRepository.countByVoucherId(id);
        if (usageCount > 0) {
            throw new ConflictException("Cannot delete voucher that has been used " + usageCount + " time(s)");
        }
        voucherRepository.delete(voucher);
    }

    @Auditable(action = "BULK_ASSIGN", entityType = "Voucher", entityIdSpel = "#voucherId")
    @Transactional
    public BulkOperationResponse bulkAssign(Long voucherId, BulkAssignRequest req) {
        Voucher voucher = findById(voucherId);
        int processed = 0, skipped = 0;
        List<BulkOperationResponse.BulkError> errors = new ArrayList<>();
        for (Long customerId : req.getCustomerIds()) {
            try {
                if (voucherCustomerRepository.existsByVoucherIdAndCustomerId(voucherId, customerId)) {
                    skipped++;
                } else {
                    com.smartvoucher.entity.Customer customer = customerRepository.findById(customerId)
                            .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
                    VoucherCustomer vc = new VoucherCustomer();
                    vc.setVoucher(voucher);
                    vc.setCustomer(customer);
                    voucherCustomerRepository.save(vc);
                    processed++;
                }
            } catch (Exception e) {
                errors.add(BulkOperationResponse.BulkError.builder().ref(customerId).reason(e.getMessage()).build());
            }
        }
        return BulkOperationResponse.builder()
                .total(req.getCustomerIds().size())
                .processed(processed)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    @Auditable(action = "CLONE", entityType = "Voucher", entityIdSpel = "#id")
    @Transactional
    public VoucherResponse clone(Long id) {
        Voucher original = findById(id);
        String newCode = original.getCode() + "_COPY_" + System.currentTimeMillis();
        if (newCode.length() > 50) newCode = newCode.substring(0, 50);
        User currentUser = getCurrentUser();
        Voucher cloned = new Voucher();
        cloned.setCode(newCode);
        cloned.setCampaign(original.getCampaign());
        cloned.setDescription(original.getDescription());
        cloned.setDiscountType(original.getDiscountType());
        cloned.setDiscountValue(original.getDiscountValue());
        cloned.setMaxDiscountAmount(original.getMaxDiscountAmount());
        cloned.setMinOrderValue(original.getMinOrderValue());
        cloned.setApplicableProducts(new ArrayList<>(original.getApplicableProducts()));
        cloned.setApplicableCategories(new ArrayList<>(original.getApplicableCategories()));
        cloned.setApplicableBranches(new ArrayList<>(original.getApplicableBranches()));
        cloned.setMaxUsageTotal(original.getMaxUsageTotal());
        cloned.setMaxUsagePerCustomer(original.getMaxUsagePerCustomer());
        cloned.setIsPublic(original.getIsPublic());
        cloned.setValidFrom(original.getValidFrom());
        cloned.setValidUntil(original.getValidUntil());
        cloned.setStatus(VoucherStatus.ACTIVE);
        cloned.setCurrentUsageCount(0);
        cloned.setCodeType(original.getCodeType());
        cloned.setCreatedBy(currentUser);
        return VoucherResponse.from(voucherRepository.save(cloned));
    }

    @Auditable(action = "PAUSE", entityType = "Voucher", entityIdSpel = "#id")
    @Transactional
    public VoucherResponse pause(Long id) {
        Voucher voucher = findById(id);
        if (voucher.getStatus() == VoucherStatus.PAUSED) {
            throw new com.smartvoucher.exception.ConflictException("Voucher is already PAUSED");
        }
        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new IllegalArgumentException("Only ACTIVE vouchers can be paused. Current status: " + voucher.getStatus());
        }
        voucher.setStatus(VoucherStatus.PAUSED);
        return VoucherResponse.from(voucherRepository.save(voucher));
    }

    @Auditable(action = "RESUME", entityType = "Voucher", entityIdSpel = "#id")
    @Transactional
    public VoucherResponse resume(Long id) {
        Voucher voucher = findById(id);
        if (voucher.getStatus() != VoucherStatus.PAUSED) {
            throw new com.smartvoucher.exception.ConflictException("Only PAUSED vouchers can be resumed. Current status: " + voucher.getStatus());
        }
        if (voucher.getValidUntil() != null && voucher.getValidUntil().isBefore(java.time.OffsetDateTime.now())) {
            throw new IllegalArgumentException("Voucher has expired (validUntil in the past), cannot resume");
        }
        voucher.setStatus(VoucherStatus.ACTIVE);
        return VoucherResponse.from(voucherRepository.save(voucher));
    }

    @Auditable(action = "BULK_DISTRIBUTE", entityType = "Voucher", entityIdSpel = "#voucherId")
    @Transactional
    public BulkDistributeResponse bulkDistribute(Long voucherId) {
        Voucher voucher = findById(voucherId); // includes ownership check

        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new IllegalArgumentException("Bulk distribution requires an ACTIVE voucher. Current status: " + voucher.getStatus());
        }

        List<VoucherCustomer> assignments = voucherCustomerRepository.findByVoucherId(voucherId);
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("No customers assigned to this voucher");
        }

        int sent = 0, skipped = 0, failed = 0;
        List<BulkDistributeResponse.BulkError> errors = new ArrayList<>();

        for (VoucherCustomer vc : assignments) {
            Long customerId = vc.getCustomer().getId();
            try {
                if (distributionRepository.existsByVoucherIdAndCustomerId(voucherId, customerId)) {
                    skipped++;
                    continue;
                }
                VoucherDistribution dist = new VoucherDistribution();
                dist.setVoucher(voucher);
                dist.setCustomer(vc.getCustomer());
                dist.setChannel(DistributionChannel.EMAIL);
                dist.setStatus(DistributionStatus.PENDING);
                VoucherDistribution saved = distributionRepository.save(dist);
                distributionProcessor.processSync(saved.getId());
                VoucherDistribution updated = distributionRepository.findById(saved.getId()).orElse(saved);
                if (updated.getStatus() == DistributionStatus.SENT) {
                    sent++;
                } else {
                    failed++;
                    errors.add(new BulkDistributeResponse.BulkError(customerId, updated.getErrorMessage()));
                }
            } catch (Exception e) {
                failed++;
                errors.add(new BulkDistributeResponse.BulkError(customerId, e.getMessage()));
            }
        }

        return BulkDistributeResponse.builder()
                .total(assignments.size())
                .sent(sent)
                .skipped(skipped)
                .failed(failed)
                .errors(errors)
                .build();
    }

    private Voucher findById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + id));
        checkOwnership(voucher);
        return voucher;
    }

    private void checkOwnership(Voucher voucher) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)
                && !voucher.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Voucher not found: " + voucher.getId());
        }
    }

    private Specification<Voucher> withOwnerFilter(Specification<Voucher> spec) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)) {
            User owner = currentUser;
            Specification<Voucher> ownerSpec = (root, query, cb) -> cb.equal(root.get("createdBy"), owner);
            return spec == null ? ownerSpec : spec.and(ownerSpec);
        }
        return spec != null ? spec : Specification.where(null);
    }

    private boolean isRestricted(User user) {
        return user.getRole() == UserRole.STAFF || user.getRole() == UserRole.USER;
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
