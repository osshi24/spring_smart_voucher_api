package com.smartvoucher.service;

import com.smartvoucher.dto.request.ApiKeyCreateRequest;
import com.smartvoucher.dto.request.RateLimitUpdateRequest;
import com.smartvoucher.dto.response.ApiKeyResponse;
import com.smartvoucher.dto.response.ApiKeyUsageResponse;
import com.smartvoucher.entity.ApiKey;
import com.smartvoucher.entity.User;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.ApiKeyRepository;
import com.smartvoucher.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;

    @Transactional
    public ApiKeyResponse create(ApiKeyCreateRequest req) {
        User currentUser = getCurrentUser();
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String plainTextKey = "sv_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String keyHash = passwordEncoder.encode(plainTextKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setName(req.getName());
        apiKey.setSystemName(req.getSystemName());
        apiKey.setKeyHash(keyHash);
        apiKey.setIsActive(true);
        apiKey.setExpiresAt(req.getExpiresAt());
        apiKey.setCreatedBy(currentUser);

        ApiKey saved = apiKeyRepository.save(apiKey);
        return ApiKeyResponse.fromWithKey(saved, plainTextKey);
    }

    @Transactional(readOnly = true)
    public Page<ApiKeyResponse> getAll(Specification<ApiKey> spec, Pageable pageable) {
        return apiKeyRepository.findAll(spec != null ? spec : Specification.where(null), pageable)
                .map(ApiKeyResponse::from);
    }

    @Transactional(readOnly = true)
    public ApiKeyResponse getById(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));
        return ApiKeyResponse.from(apiKey);
    }

    @Transactional
    public ApiKeyResponse updateRateLimit(Long id, RateLimitUpdateRequest request) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));
        if (request.getRateLimitPerMinute() != null) apiKey.setRateLimitPerMinute(request.getRateLimitPerMinute());
        if (request.getRateLimitPerDay() != null) apiKey.setRateLimitPerDay(request.getRateLimitPerDay());
        return ApiKeyResponse.from(apiKeyRepository.save(apiKey));
    }

    @Transactional(readOnly = true)
    public ApiKeyUsageResponse getUsage(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));
        return ApiKeyUsageResponse.builder()
                .apiKeyId(id)
                .name(apiKey.getName())
                .todayRequests(rateLimitService.getDailyUsage(id))
                .thisMinuteRequests(rateLimitService.getMinuteUsage(id))
                .limitPerMinute(apiKey.getRateLimitPerMinute())
                .limitPerDay(apiKey.getRateLimitPerDay())
                .build();
    }

    @Transactional
    public ApiKeyResponse deactivate(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));
        apiKey.setIsActive(false);
        return ApiKeyResponse.from(apiKeyRepository.save(apiKey));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
