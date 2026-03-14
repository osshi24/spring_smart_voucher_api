package com.smartvoucher.dto.response;

import com.smartvoucher.entity.MerchantWebhook;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class WebhookResponse {
    private Long id;
    private String url;
    private String[] events;
    private Boolean isActive;
    private Integer failureCount;
    private OffsetDateTime lastTriggeredAt;
    private OffsetDateTime createdAt;

    public static WebhookResponse from(MerchantWebhook webhook) {
        WebhookResponse res = new WebhookResponse();
        res.id = webhook.getId();
        res.url = webhook.getUrl();
        res.events = webhook.getEvents();
        res.isActive = webhook.getIsActive();
        res.failureCount = webhook.getFailureCount();
        res.lastTriggeredAt = webhook.getLastTriggeredAt();
        res.createdAt = webhook.getCreatedAt();
        return res;
    }
}
