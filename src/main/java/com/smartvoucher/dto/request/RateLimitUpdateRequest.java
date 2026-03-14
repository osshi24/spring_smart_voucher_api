package com.smartvoucher.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RateLimitUpdateRequest {

    @Min(value = 1, message = "rateLimitPerMinute must be at least 1")
    private Integer rateLimitPerMinute;

    @Min(value = 0, message = "rateLimitPerDay must be at least 0 (0 = unlimited)")
    private Integer rateLimitPerDay;
}
