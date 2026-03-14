package com.smartvoucher.service;

import com.smartvoucher.dto.request.ChangePasswordRequest;
import com.smartvoucher.entity.PasswordResetOtp;
import com.smartvoucher.entity.User;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.PasswordResetOtpRepository;
import com.smartvoucher.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.email.otp-expiry-minutes:15}")
    private int otpExpiryMinutes;

    @Value("${app.email.otp-max-attempts:5}")
    private int otpMaxAttempts;

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void forgotPasswordOtp(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetOtpRepository.deleteByUser(user);

            String otp = String.format("%06d", new Random().nextInt(1_000_000));
            PasswordResetOtp record = PasswordResetOtp.builder()
                    .user(user)
                    .otpHash(hashSha256(otp))
                    .otpExpiresAt(OffsetDateTime.now().plusMinutes(otpExpiryMinutes))
                    .build();
            passwordResetOtpRepository.save(record);
            sendOtpEmailAsync(user.getEmail(), otp);
        });
        // Always return silently — do not reveal if email exists
    }

    @Transactional
    public String verifyOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or OTP"));

        PasswordResetOtp record = passwordResetOtpRepository
                .findTopByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalArgumentException("No OTP request found"));

        if (Boolean.TRUE.equals(record.getUsed())) {
            throw new IllegalArgumentException("OTP already used");
        }
        if (record.getOtpExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("OTP expired");
        }
        if (record.getAttempts() >= otpMaxAttempts) {
            throw new IllegalArgumentException("Too many failed attempts. Please request a new OTP");
        }
        if (!record.getOtpHash().equals(hashSha256(otp))) {
            record.setAttempts(record.getAttempts() + 1);
            passwordResetOtpRepository.save(record);
            int remaining = otpMaxAttempts - record.getAttempts();
            throw new IllegalArgumentException("Invalid OTP. " + remaining + " attempt(s) remaining");
        }

        String resetToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        record.setResetToken(resetToken);
        record.setResetTokenExpiresAt(OffsetDateTime.now().plusMinutes(5));
        passwordResetOtpRepository.save(record);

        return resetToken;
    }

    @Transactional
    public void resetPasswordByOtp(String resetToken, String newPassword) {
        PasswordResetOtp record = passwordResetOtpRepository.findByResetToken(resetToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (Boolean.TRUE.equals(record.getUsed())) {
            throw new IllegalArgumentException("Reset token already used");
        }
        if (record.getResetTokenExpiresAt() == null ||
                record.getResetTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Reset token expired");
        }

        record.setUsed(true);
        passwordResetOtpRepository.save(record);

        User user = record.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void adminResetPassword(Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        if (user.getEmail() != null) {
            sendTempPasswordEmailAsync(user.getEmail(), user.getUsername(), tempPassword);
        }
    }

    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Async("emailTaskExecutor")
    public void sendOtpEmailAsync(String email, String otp) {
        try {
            String body = """
                    <h2>Password Reset OTP</h2>
                    <p>Your OTP code is:</p>
                    <h1 style="letter-spacing: 8px;">%s</h1>
                    <p>This code expires in %d minutes. Do not share it with anyone.</p>
                    <p>If you did not request this, ignore this email.</p>
                    """.formatted(otp, otpExpiryMinutes);
            emailService.sendHtmlEmail(email, "Smart Voucher - Password Reset OTP", body);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage());
        }
    }

    @Async("emailTaskExecutor")
    public void sendTempPasswordEmailAsync(String email, String username, String tempPassword) {
        try {
            String body = """
                    <h2>Your password has been reset</h2>
                    <p>Hello %s,</p>
                    <p>An administrator has reset your password. Your temporary password is:</p>
                    <p><strong>%s</strong></p>
                    <p>Please log in and change your password immediately.</p>
                    """.formatted(username, tempPassword);
            emailService.sendHtmlEmail(email, "Smart Voucher - Password Reset by Admin", body);
        } catch (Exception e) {
            log.error("Failed to send temp password email to {}: {}", email, e.getMessage());
        }
    }
}
