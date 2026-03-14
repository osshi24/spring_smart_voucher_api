package com.smartvoucher.service;

import com.smartvoucher.dto.request.RegisterRequest;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.EmailVerificationOtp;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.entity.enums.UserStatus;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.EmailVerificationOtpRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final EmailVerificationOtpRepository emailVerificationOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final CustomerRepository customerRepository;

    @Value("${app.email.otp-expiry-minutes:15}")
    private int otpExpiryMinutes;

    @Value("${app.email.otp-max-attempts:5}")
    private int otpMaxAttempts;

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

        sendOtp(user);
        return user.getId();
    }

    @Transactional
    public Long registerMerchant(RegisterRequest request) {
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
                .role(UserRole.USER)
                .status(UserStatus.PENDING)
                .isActive(true)
                .emailVerified(false)
                .build();
        user = userRepository.save(user);

        // Create a linked customer record for the merchant
        Customer customer = new Customer();
        customer.setFullName(request.getFullName());
        customer.setEmail(request.getEmail());
        customer.setExternalId("user:" + user.getId());
        customer.setIsActive(true);
        customer.setCreatedBy(user);
        customerRepository.save(customer);

        sendOtp(user);
        return user.getId();
    }

    @Transactional
    public void verifyEmail(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or OTP"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Email already verified");
        }

        EmailVerificationOtp record = emailVerificationOtpRepository
                .findTopByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalArgumentException("No OTP request found"));

        if (Boolean.TRUE.equals(record.getUsed())) {
            throw new IllegalArgumentException("OTP already used");
        }
        if (record.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("OTP expired");
        }
        if (record.getAttempts() >= otpMaxAttempts) {
            throw new IllegalArgumentException("Too many failed attempts. Please request a new OTP");
        }
        if (!record.getOtpHash().equals(hashSha256(otp))) {
            record.setAttempts(record.getAttempts() + 1);
            emailVerificationOtpRepository.save(record);
            int remaining = otpMaxAttempts - record.getAttempts();
            throw new IllegalArgumentException("Invalid OTP. " + remaining + " attempt(s) remaining");
        }

        record.setUsed(true);
        emailVerificationOtpRepository.save(record);

        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Email already verified");
        }

        sendOtp(user);
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

    private void sendOtp(User user) {
        emailVerificationOtpRepository.deleteByUser(user);
        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        EmailVerificationOtp record = EmailVerificationOtp.builder()
                .user(user)
                .otpHash(hashSha256(otp))
                .expiresAt(OffsetDateTime.now().plusMinutes(otpExpiryMinutes))
                .build();
        emailVerificationOtpRepository.save(record);
        sendOtpEmailAsync(user.getEmail(), otp);
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

    @Async("emailTaskExecutor")
    public void sendOtpEmailAsync(String email, String otp) {
        try {
            String body = """
                    <h2>Verify Your Email</h2>
                    <p>Your verification code is:</p>
                    <h1 style="letter-spacing: 8px;">%s</h1>
                    <p>This code expires in %d minutes. Do not share it with anyone.</p>
                    """.formatted(otp, otpExpiryMinutes);
            emailService.sendHtmlEmail(email, "Smart Voucher - Verify Your Email", body);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage());
        }
    }
}
