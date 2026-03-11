package com.smartvoucher.dto.request;

import com.smartvoucher.entity.enums.CampaignStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class CampaignCreateRequest {

    @NotBlank(message = "Campaign name is required")
    private String name;

    private String description;

    private BigDecimal budget;

    @NotNull(message = "Start date is required")
    private OffsetDateTime startDate;

    @NotNull(message = "End date is required")
    private OffsetDateTime endDate;

    private CampaignStatus status = CampaignStatus.DRAFT;
}
