package com.smartvoucher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoucher.dto.request.WebhookRequest;
import com.smartvoucher.dto.response.WebhookResponse;
import com.smartvoucher.entity.MerchantWebhook;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.VoucherUsage;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.MerchantWebhookRepository;
import com.smartvoucher.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final int MAX_FAILURES = 10;
    private static final int MAX_RETRIES = 3;

    private final MerchantWebhookRepository webhookRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public WebhookResponse create(WebhookRequest req) {
        User user = getCurrentUser();
        MerchantWebhook webhook = MerchantWebhook.builder()
                .user(user)
                .url(req.getUrl())
                .secret(req.getSecret())
                .events(req.getEvents())
                .build();
        return WebhookResponse.from(Objects.requireNonNull(webhookRepository.save(webhook)));
    }

    @Transactional(readOnly = true)
    public List<WebhookResponse> getAll() {
        User user = getCurrentUser();
        return webhookRepository.findByUserId(Objects.requireNonNull(user.getId()))
                .stream().map(WebhookResponse::from).toList();
    }

    @Transactional
    public WebhookResponse update(Long id, WebhookRequest req) {
        MerchantWebhook webhook = webhookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));
        webhook.setUrl(req.getUrl());
        webhook.setSecret(req.getSecret());
        if (req.getEvents() != null) webhook.setEvents(req.getEvents());
        return WebhookResponse.from(Objects.requireNonNull(webhookRepository.save(webhook)));
    }

    @Transactional
    public void delete(Long id) {
        webhookRepository.deleteById(Objects.requireNonNull(id));
    }

    @Async
    public void dispatchRedemptionEvent(Long merchantUserId, VoucherUsage usage) {
        List<MerchantWebhook> hooks = webhookRepository.findByUserIdAndIsActiveTrue(merchantUserId);
        for (MerchantWebhook hook : hooks) {
            try {
                String payload = buildPayload(usage);
                dispatchWithRetry(hook, payload, 0);
            } catch (Exception e) {
                log.warn("Webhook dispatch failed for hook {}: {}", hook.getId(), e.getMessage());
            }
        }
    }

    private void dispatchWithRetry(MerchantWebhook hook, String payload, int attempt) {
        try {
            String signature = hmacSha256(Objects.requireNonNull(hook.getSecret()), payload);
            RestClient.create()
                    .post()
                    .uri(URI.create(Objects.requireNonNull(hook.getUrl())))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Event", "voucher.redeemed")
                    .header("X-Webhook-Signature", "sha256=" + signature)
                    .header("X-Webhook-Timestamp", String.valueOf(System.currentTimeMillis() / 1000))
                    .header("User-Agent", "SmartVoucher-Webhook/1.0")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            hook.setFailureCount(0);
            hook.setLastTriggeredAt(OffsetDateTime.now());
            webhookRepository.save(hook);
        } catch (Exception e) {
            hook.setFailureCount(hook.getFailureCount() + 1);
            if (hook.getFailureCount() >= MAX_FAILURES) {
                hook.setIsActive(false);
                log.warn("Webhook {} auto-disabled after {} failures", hook.getId(), MAX_FAILURES);
            }
            webhookRepository.save(hook);

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep((long) Math.pow(2, attempt) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                dispatchWithRetry(hook, payload, attempt + 1);
            }
        }
    }

    private String buildPayload(VoucherUsage usage) throws Exception {
        Map<String, Object> data = Map.of(
                "event", "voucher.redeemed",
                "timestamp", OffsetDateTime.now().toString(),
                "data", Map.of(
                        "voucherCode", Objects.requireNonNull(usage.getVoucher().getCode()),
                        "customerId", Objects.requireNonNull(usage.getCustomer().getId()),
                        "customerName", Objects.requireNonNull(usage.getCustomer().getFullName()),
                        "discountAmount", usage.getDiscountAmount(),
                        "orderAmount", usage.getOrderTotal(),
                        "externalOrderId", usage.getExternalOrderId() != null ? usage.getExternalOrderId() : ""
                )
        );
        return objectMapper.writeValueAsString(data);
    }

    private String hmacSha256(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
