package com.smartvoucher.service;

import com.smartvoucher.dto.request.RegisterRequest;
import com.smartvoucher.entity.EmailVerificationToken;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.entity.enums.UserStatus;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.EmailVerificationTokenRepository;
import com.smartvoucher.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.email.verification-expiry-hours:24}")
    private int verificationExpiryHours;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public Long register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(UserRole.STAFF)
                .status(UserStatus.PENDING)
                .isActive(true)
                .emailVerified(false)
                .build();
        user = userRepository.save(user);

        EmailVerificationToken token = generateVerificationToken(user);
        sendVerificationEmailAsync(user.getEmail(), token.getToken());

        return user.getId();
    }

    @Transactional
    public void verifyEmail(String tokenValue) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (Boolean.TRUE.equals(token.getUsed())) {
            throw new IllegalArgumentException("Verification token already used");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Verification token expired");
        }

        token.setUsed(true);
        emailVerificationTokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerification(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Email already verified");
        }

        emailVerificationTokenRepository.invalidateAllForUser(user);
        EmailVerificationToken token = generateVerificationToken(user);
        sendVerificationEmailAsync(user.getEmail(), token.getToken());
    }

    @Transactional
    public void approveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new IllegalArgumentException("User is not in PENDING status");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("User email has not been verified yet");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Transactional
    public void rejectUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new IllegalArgumentException("User is not in PENDING status");
        }

        user.setStatus(UserStatus.REJECTED);
        user.setIsActive(false);
        userRepository.save(user);
    }

    private EmailVerificationToken generateVerificationToken(User user) {
        String tokenValue = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(OffsetDateTime.now().plusHours(verificationExpiryHours))
                .used(false)
                .build();
        return emailVerificationTokenRepository.save(token);
    }

    @Async("emailTaskExecutor")
    public void sendVerificationEmailAsync(String email, String token) {
        try {
            String link = baseUrl + "/api/v1/auth/verify-email?token=" + token;
            String body = """
                    <h2>Verify your email</h2>
                    <p>Click the link below to verify your email address:</p>
                    <a href="%s">Verify Email</a>
                    <p>This link expires in %d hours.</p>
                    """.formatted(link, verificationExpiryHours);
            emailService.sendHtmlEmail(email, "Smart Voucher - Verify Your Email", body);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", email, e.getMessage());
        }
    }
}
