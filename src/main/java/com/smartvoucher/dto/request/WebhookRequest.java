package com.smartvoucher.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebhookRequest {

    @NotBlank(message = "URL is required")
    private String url;

    @NotBlank(message = "Secret is required")
    private String secret;

    private String[] events = new String[]{"voucher.redeemed"};
}
