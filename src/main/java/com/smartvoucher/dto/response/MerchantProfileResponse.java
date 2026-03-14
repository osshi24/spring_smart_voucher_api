package com.smartvoucher.dto.response;

import com.smartvoucher.entity.MerchantProfile;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MerchantProfileResponse {
    private String businessName;
    private String businessType;
    private String address;
    private String logoUrl;
    private String taxCode;
    private Integer maxApiKeys;

    public static MerchantProfileResponse from(MerchantProfile p) {
        MerchantProfileResponse res = new MerchantProfileResponse();
        res.businessName = p.getBusinessName();
        res.businessType = p.getBusinessType();
        res.address = p.getAddress();
        res.logoUrl = p.getLogoUrl();
        res.taxCode = p.getTaxCode();
        res.maxApiKeys = p.getMaxApiKeys();
        return res;
    }
}
