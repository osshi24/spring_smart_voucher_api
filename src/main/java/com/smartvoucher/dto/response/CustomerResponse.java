package com.smartvoucher.dto.response;

import com.smartvoucher.entity.Customer;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class CustomerResponse {

    private Long id;
    private String externalId;
    private String fullName;
    private String email;
    private String phone;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static CustomerResponse from(Customer customer) {
        CustomerResponse res = new CustomerResponse();
        res.id = customer.getId();
        res.externalId = customer.getExternalId();
        res.fullName = customer.getFullName();
        res.email = customer.getEmail();
        res.phone = customer.getPhone();
        res.isActive = customer.getIsActive();
        res.createdAt = customer.getCreatedAt();
        res.updatedAt = customer.getUpdatedAt();
        return res;
    }
}
