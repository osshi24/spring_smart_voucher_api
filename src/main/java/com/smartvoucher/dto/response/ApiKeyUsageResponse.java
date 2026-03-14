package com.smartvoucher.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiKeyUsageResponse {
    private Long apiKeyId;
    private String name;
    private long todayRequests;
    private long thisMinuteRequests;
    private Integer limitPerMinute;
    private Integer limitPerDay;
}
