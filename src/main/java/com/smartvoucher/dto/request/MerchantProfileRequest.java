package com.smartvoucher.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MerchantProfileRequest {
    private String businessName;
    private String businessType;
    private String address;
    private String logoUrl;
    private String taxCode;
}
