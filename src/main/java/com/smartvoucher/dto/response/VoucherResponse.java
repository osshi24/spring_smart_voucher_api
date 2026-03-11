package com.smartvoucher.dto.response;

import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class VoucherResponse {

    private Long id;
    private String code;
    private Long campaignId;
    private String campaignName;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderValue;
    private List<String> applicableProducts;
    private List<String> applicableCategories;
    private List<String> applicableBranches;
    private Integer maxUsageTotal;
    private Integer maxUsagePerCustomer;
    private Integer currentUsageCount;
    private Boolean isPublic;
    private OffsetDateTime validFrom;
    private OffsetDateTime validUntil;
    private VoucherStatus status;
    private Long createdById;
    private String createdByUsername;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static VoucherResponse from(Voucher voucher) {
        VoucherResponse res = new VoucherResponse();
        res.id = voucher.getId();
        res.code = voucher.getCode();
        if (voucher.getCampaign() != null) {
            res.campaignId = voucher.getCampaign().getId();
            res.campaignName = voucher.getCampaign().getName();
        }
        res.description = voucher.getDescription();
        res.discountType = voucher.getDiscountType();
        res.discountValue = voucher.getDiscountValue();
        res.maxDiscountAmount = voucher.getMaxDiscountAmount();
        res.minOrderValue = voucher.getMinOrderValue();
        res.applicableProducts = voucher.getApplicableProducts();
        res.applicableCategories = voucher.getApplicableCategories();
        res.applicableBranches = voucher.getApplicableBranches();
        res.maxUsageTotal = voucher.getMaxUsageTotal();
        res.maxUsagePerCustomer = voucher.getMaxUsagePerCustomer();
        res.currentUsageCount = voucher.getCurrentUsageCount();
        res.isPublic = voucher.getIsPublic();
        res.validFrom = voucher.getValidFrom();
        res.validUntil = voucher.getValidUntil();
        res.status = voucher.getStatus();
        if (voucher.getCreatedBy() != null) {
            res.createdById = voucher.getCreatedBy().getId();
            res.createdByUsername = voucher.getCreatedBy().getUsername();
        }
        res.createdAt = voucher.getCreatedAt();
        res.updatedAt = voucher.getUpdatedAt();
        return res;
    }
}
