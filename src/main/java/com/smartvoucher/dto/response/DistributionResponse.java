package com.smartvoucher.dto.response;

import com.smartvoucher.entity.VoucherDistribution;
import com.smartvoucher.entity.enums.DistributionChannel;
import com.smartvoucher.entity.enums.DistributionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class DistributionResponse {

    private Long id;
    private Long voucherId;
    private String voucherCode;
    private Long customerId;
    private String customerName;
    private DistributionChannel channel;
    private DistributionStatus status;
    private OffsetDateTime sentAt;
    private String errorMessage;
    private OffsetDateTime createdAt;

    public static DistributionResponse from(VoucherDistribution dist) {
        DistributionResponse res = new DistributionResponse();
        res.id = dist.getId();
        if (dist.getVoucher() != null) {
            res.voucherId = dist.getVoucher().getId();
            res.voucherCode = dist.getVoucher().getCode();
        }
        if (dist.getCustomer() != null) {
            res.customerId = dist.getCustomer().getId();
            res.customerName = dist.getCustomer().getFullName();
        }
        res.channel = dist.getChannel();
        res.status = dist.getStatus();
        res.sentAt = dist.getSentAt();
        res.errorMessage = dist.getErrorMessage();
        res.createdAt = dist.getCreatedAt();
        return res;
    }
}
