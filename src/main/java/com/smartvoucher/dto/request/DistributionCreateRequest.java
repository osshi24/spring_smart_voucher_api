package com.smartvoucher.dto.request;

import com.smartvoucher.entity.enums.DistributionChannel;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DistributionCreateRequest {

    @NotNull(message = "Voucher ID is required")
    private Long voucherId;

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Channel is required")
    private DistributionChannel channel;
}
