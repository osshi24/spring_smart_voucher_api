package com.smartvoucher.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerCreateRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String externalId;

    private String email;

    private String phone;
}
