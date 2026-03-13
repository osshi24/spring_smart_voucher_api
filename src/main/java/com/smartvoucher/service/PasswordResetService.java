package com.smartvoucher.service;

import com.smartvoucher.dto.request.ChangePasswordRequest;
import com.smartvoucher.entity.PasswordResetToken;
import com.smartvoucher.entity.User;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.PasswordResetTokenRepository;
import com.smartvoucher.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.email.reset-token-expiry-hours:1}")
    private int resetTokenExpiryHours;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

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
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUser(user);
            String tokenValue = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
            PasswordResetToken token = PasswordResetToken.builder()
                    .user(user)
                    .token(tokenValue)
                    .expiresAt(OffsetDateTime.now().plusHours(resetTokenExpiryHours))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(token);
            sendResetEmailAsync(user.getEmail(), tokenValue);
        });
        // Always return silently — do not reveal if email exists
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (Boolean.TRUE.equals(token.getUsed())) {
            throw new IllegalArgumentException("Reset token already used");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Reset token expired");
        }

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        User user = token.getUser();
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
    public void sendResetEmailAsync(String email, String token) {
        try {
            String link = baseUrl + "/api/v1/auth/reset-password?token=" + token;
            String body = """
                    <h2>Password Reset Request</h2>
                    <p>Click the link below to reset your password:</p>
                    <a href="%s">Reset Password</a>
                    <p>This link expires in %d hour(s). If you did not request this, ignore this email.</p>
                    """.formatted(link, resetTokenExpiryHours);
            emailService.sendHtmlEmail(email, "Smart Voucher - Password Reset", body);
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", email, e.getMessage());
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
