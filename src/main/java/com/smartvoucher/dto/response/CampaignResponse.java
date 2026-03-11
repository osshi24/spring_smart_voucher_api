package com.smartvoucher.dto.response;

import com.smartvoucher.entity.Campaign;
import com.smartvoucher.entity.enums.CampaignStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class CampaignResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal budget;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private CampaignStatus status;
    private Long createdById;
    private String createdByUsername;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static CampaignResponse from(Campaign campaign) {
        CampaignResponse res = new CampaignResponse();
        res.id = campaign.getId();
        res.name = campaign.getName();
        res.description = campaign.getDescription();
        res.budget = campaign.getBudget();
        res.startDate = campaign.getStartDate();
        res.endDate = campaign.getEndDate();
        res.status = campaign.getStatus();
        if (campaign.getCreatedBy() != null) {
            res.createdById = campaign.getCreatedBy().getId();
            res.createdByUsername = campaign.getCreatedBy().getUsername();
        }
        res.createdAt = campaign.getCreatedAt();
        res.updatedAt = campaign.getUpdatedAt();
        return res;
    }
}
