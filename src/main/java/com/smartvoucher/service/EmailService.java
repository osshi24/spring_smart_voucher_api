package com.smartvoucher.service;

import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.enums.DiscountType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@smartvoucher.com}")
    private String fromAddress;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.debug("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }

    public void sendTextEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.debug("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }

    public void sendVoucherEmail(String to, Voucher voucher, String customerName, byte[] qrImageBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Voucher " + voucher.getCode() + " - Smart Voucher");

            String discountDisplay = voucher.getDiscountType() == DiscountType.PERCENTAGE
                    ? "Giảm " + voucher.getDiscountValue().toPlainString() + "%"
                    : "Giảm " + voucher.getDiscountValue().toPlainString() + " VNĐ";

            String validUntil = voucher.getValidUntil() != null
                    ? voucher.getValidUntil().format(DATE_FMT)
                    : "Không giới hạn";

            String minOrder = voucher.getMinOrderValue() != null
                    ? voucher.getMinOrderValue().toPlainString() + " VNĐ"
                    : "0 VNĐ";

            String html = buildVoucherEmailHtml(
                    customerName,
                    voucher.getCode(),
                    voucher.getDescription() != null ? voucher.getDescription() : voucher.getCode(),
                    discountDisplay,
                    minOrder,
                    validUntil
            );

            helper.setText(html, true);

            if (qrImageBytes != null && qrImageBytes.length > 0) {
                helper.addInline("qrImage", new ByteArrayResource(qrImageBytes), "image/png");
            }

            mailSender.send(message);
            log.debug("Voucher email sent to {}: voucher={}", to, voucher.getCode());
        } catch (Exception e) {
            log.error("Failed to send voucher email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Voucher email sending failed", e);
        }
    }

    private String buildVoucherEmailHtml(String customerName, String code, String voucherName,
                                          String discountDisplay, String minOrderValue, String validUntil) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <style>
                    body{font-family:Arial,sans-serif;background:#f5f5f5;margin:0;padding:0}
                    .container{max-width:600px;margin:30px auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.1)}
                    .header{background:#2c7be5;color:#fff;padding:24px;text-align:center}
                    .body{padding:32px}
                    .voucher-box{background:#f0f7ff;border:2px dashed #2c7be5;border-radius:8px;padding:20px;text-align:center;margin:24px 0}
                    .voucher-code{font-size:28px;font-weight:bold;color:#2c7be5;letter-spacing:4px}
                    .row{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #eee;font-size:14px}
                    .qr-section{text-align:center;margin:24px 0}
                    .footer{background:#f9f9f9;padding:16px;text-align:center;color:#aaa;font-size:12px}
                  </style>
                </head>
                <body>
                <div class="container">
                  <div class="header"><h1 style="margin:0;font-size:22px">Smart Voucher</h1></div>
                  <div class="body">
                    <p>Xin chào <strong>%s</strong>,</p>
                    <p>Bạn vừa nhận được voucher ưu đãi. Xuất trình mã QR khi thanh toán.</p>
                    <div class="voucher-box">
                      <div style="font-size:12px;color:#666;margin-bottom:6px">MÃ VOUCHER</div>
                      <div class="voucher-code">%s</div>
                      <div style="font-size:18px;color:#333;margin-top:8px">%s</div>
                    </div>
                    <div class="row"><span style="color:#666">Tên voucher</span><strong>%s</strong></div>
                    <div class="row"><span style="color:#666">Đơn hàng tối thiểu</span><strong>%s</strong></div>
                    <div class="row"><span style="color:#666">Hiệu lực đến</span><strong>%s</strong></div>
                    <div class="qr-section">
                      <p><strong>Quét QR tại quầy để sử dụng voucher</strong></p>
                      <img src="cid:qrImage" alt="QR Code" style="width:200px;height:200px;border:1px solid #eee;border-radius:4px"/>
                      <p style="font-size:12px;color:#999">Mã QR có hiệu lực trong 24 giờ</p>
                    </div>
                  </div>
                  <div class="footer">Email tự động từ Smart Voucher. Vui lòng không trả lời.</div>
                </div>
                </body></html>
                """.formatted(customerName, code, discountDisplay, voucherName, minOrderValue, validUntil);
    }
}
