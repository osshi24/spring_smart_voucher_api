package com.smartvoucher.service;

import com.smartvoucher.dto.request.CampaignCreateRequest;
import com.smartvoucher.dto.response.CampaignResponse;
import com.smartvoucher.dto.response.CampaignStatsResponse;
import com.smartvoucher.dto.response.VoucherResponse;
import com.smartvoucher.entity.Campaign;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.CampaignStatus;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CampaignRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import com.smartvoucher.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public CampaignResponse create(CampaignCreateRequest req) {
        if (req.getEndDate() != null && req.getStartDate() != null &&
                !req.getEndDate().isAfter(req.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        User currentUser = getCurrentUser();
        Campaign campaign = new Campaign();
        campaign.setName(req.getName());
        campaign.setDescription(req.getDescription());
        campaign.setBudget(req.getBudget());
        campaign.setStartDate(req.getStartDate());
        campaign.setEndDate(req.getEndDate());
        campaign.setStatus(req.getStatus() != null ? req.getStatus() : CampaignStatus.DRAFT);
        campaign.setCreatedBy(currentUser);

        return CampaignResponse.from(campaignRepository.save(campaign));
    }

    @Transactional(readOnly = true)
    public Page<CampaignResponse> getAll(Specification<Campaign> spec, Pageable pageable) {
        return campaignRepository.findAll(withOwnerFilter(spec), pageable).map(CampaignResponse::from);
    }

    @Transactional(readOnly = true)
    public CampaignResponse getById(Long id) {
        return CampaignResponse.from(findById(id));
    }

    @Transactional
    public CampaignResponse update(Long id, CampaignCreateRequest req) {
        Campaign campaign = findById(id);

        if (req.getName() != null) campaign.setName(req.getName());
        if (req.getDescription() != null) campaign.setDescription(req.getDescription());
        if (req.getBudget() != null) campaign.setBudget(req.getBudget());
        if (req.getStartDate() != null) campaign.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) campaign.setEndDate(req.getEndDate());

        return CampaignResponse.from(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse updateStatus(Long id, CampaignStatus newStatus) {
        Campaign campaign = findById(id);
        CampaignStatus oldStatus = campaign.getStatus();
        campaign.setStatus(newStatus);
        CampaignResponse result = CampaignResponse.from(campaignRepository.save(campaign));
        auditLogService.log("STATUS_CHANGE", "Campaign", id, oldStatus, newStatus);
        return result;
    }

    @Transactional(readOnly = true)
    public CampaignStatsResponse getStats(Long id) {
        Campaign campaign = findById(id);

        long totalVouchers = voucherRepository.findByCampaignId(id, Pageable.unpaged()).getTotalElements();
        long totalRedemptions = voucherUsageRepository.countByCampaignId(id);
        BigDecimal totalDiscount = voucherUsageRepository.sumDiscountAmountByCampaignId(id);

        return new CampaignStatsResponse(
                campaign.getId(),
                campaign.getName(),
                totalVouchers,
                totalRedemptions,
                totalDiscount != null ? totalDiscount : BigDecimal.ZERO,
                campaign.getBudget()
        );
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> getCampaignVouchers(Long campaignId, String code, String status,
                                                      String discountType, java.time.OffsetDateTime validFrom,
                                                      java.time.OffsetDateTime validUntil, Pageable pageable) {
        findById(campaignId); // ownership check
        Specification<com.smartvoucher.entity.Voucher> spec =
                (root, query, cb) -> cb.equal(root.get("campaign").get("id"), campaignId);
        if (code != null && !code.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("code")), "%" + code.toLowerCase() + "%"));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status").as(String.class), status));
        }
        if (discountType != null && !discountType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("discountType").as(String.class), discountType));
        }
        if (validFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("validFrom"), validFrom));
        }
        if (validUntil != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("validUntil"), validUntil));
        }
        return voucherRepository.findAll(spec, pageable).map(VoucherResponse::from);
    }

    @Transactional
    public void delete(Long id) {
        Campaign campaign = findById(id);
        campaignRepository.delete(campaign);
    }

    @Transactional
    public CampaignResponse clone(Long id) {
        Campaign original = findById(id);
        User currentUser = getCurrentUser();
        Campaign cloned = new Campaign();
        cloned.setName("Copy of " + original.getName());
        cloned.setDescription(original.getDescription());
        cloned.setBudget(original.getBudget());
        cloned.setStartDate(original.getStartDate());
        cloned.setEndDate(original.getEndDate());
        cloned.setStatus(CampaignStatus.DRAFT);
        cloned.setCreatedBy(currentUser);
        return CampaignResponse.from(campaignRepository.save(cloned));
    }

    private Campaign findById(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + id));
        checkOwnership(campaign);
        return campaign;
    }

    private void checkOwnership(Campaign campaign) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)
                && !campaign.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Campaign not found: " + campaign.getId());
        }
    }

    private Specification<Campaign> withOwnerFilter(Specification<Campaign> spec) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)) {
            User owner = currentUser;
            Specification<Campaign> ownerSpec = (root, query, cb) -> cb.equal(root.get("createdBy"), owner);
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
