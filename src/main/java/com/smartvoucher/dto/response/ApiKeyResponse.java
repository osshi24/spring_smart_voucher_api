package com.smartvoucher.dto.response;

import com.smartvoucher.entity.ApiKey;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ApiKeyResponse {

    private Long id;
    private String name;
    private String systemName;
    private Boolean isActive;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    // Returned only on creation
    private String plainTextKey;

    public static ApiKeyResponse from(ApiKey apiKey) {
        ApiKeyResponse res = new ApiKeyResponse();
        res.id = apiKey.getId();
        res.name = apiKey.getName();
        res.systemName = apiKey.getSystemName();
        res.isActive = apiKey.getIsActive();
        res.expiresAt = apiKey.getExpiresAt();
        res.createdAt = apiKey.getCreatedAt();
        return res;
    }

    public static ApiKeyResponse fromWithKey(ApiKey apiKey, String plainTextKey) {
        ApiKeyResponse res = from(apiKey);
        res.plainTextKey = plainTextKey;
        return res;
    }
}
