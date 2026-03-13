package com.smartvoucher.event;

import com.smartvoucher.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoucherRedeemedEmailListener {

    private final EmailService emailService;

    @EventListener
    @Async("emailTaskExecutor")
    public void handleVoucherRedeemed(VoucherRedeemedEvent event) {
        if (!StringUtils.hasText(event.getCustomerEmail())) {
            log.debug("No email for customer {}, skipping redemption email", event.getCustomerId());
            return;
        }

        try {
            String discountText = event.getDiscountType() != null
                    && event.getDiscountType().name().equals("PERCENTAGE")
                    ? event.getDiscountValue() + "%"
                    : String.format("%,.0f VND", event.getDiscountValue());

            String expiryText = event.getExpiryDate() != null
                    ? event.getExpiryDate().toLocalDate().toString()
                    : "No expiry";

            String body = """
                    <h2>Voucher Redeemed Successfully!</h2>
                    <p>Thank you for using our voucher. Here are your redemption details:</p>
                    <table style="border-collapse:collapse">
                        <tr><td><strong>Voucher Code:</strong></td><td>%s</td></tr>
                        <tr><td><strong>Discount:</strong></td><td>%s</td></tr>
                        <tr><td><strong>Expiry Date:</strong></td><td>%s</td></tr>
                    </table>
                    <p>Thank you for your purchase!</p>
                    """.formatted(event.getVoucherCode(), discountText, expiryText);

            emailService.sendHtmlEmail(
                    event.getCustomerEmail(),
                    "Smart Voucher - Redemption Confirmation [" + event.getVoucherCode() + "]",
                    body
            );
        } catch (Exception e) {
            log.error("Failed to send redemption email to {}: {}", event.getCustomerEmail(), e.getMessage());
            // Never rethrow — email failure must not affect redemption
        }
    }
}
