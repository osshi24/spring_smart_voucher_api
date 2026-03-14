package com.smartvoucher.dto.response;

import com.smartvoucher.entity.VoucherCustomer;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class VoucherCustomerResponse {

    private Long id;
    private Long customerId;
    private String customerExternalId;
    private String customerName;
    private String customerEmail;
    private OffsetDateTime assignedAt;

    public static VoucherCustomerResponse from(VoucherCustomer vc) {
        VoucherCustomerResponse res = new VoucherCustomerResponse();
        res.id = vc.getId();
        if (vc.getCustomer() != null) {
            res.customerId = vc.getCustomer().getId();
            res.customerExternalId = vc.getCustomer().getExternalId();
            res.customerName = vc.getCustomer().getFullName();
            res.customerEmail = vc.getCustomer().getEmail();
        }
        res.assignedAt = vc.getCreatedAt();
        return res;
    }
}
