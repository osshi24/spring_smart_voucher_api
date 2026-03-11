package com.smartvoucher.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ApiKeyCreateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "System name is required")
    private String systemName;

    private OffsetDateTime expiresAt;
}
