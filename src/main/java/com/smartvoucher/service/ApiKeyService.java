package com.smartvoucher.service;

import com.smartvoucher.dto.request.ApiKeyCreateRequest;
import com.smartvoucher.dto.request.RateLimitUpdateRequest;
import com.smartvoucher.dto.response.ApiKeyResponse;
import com.smartvoucher.dto.response.ApiKeyUsageResponse;
import com.smartvoucher.entity.ApiKey;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.ApiKeyRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.service.AuditLogService;
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
    private final AuditLogService auditLogService;

    @Transactional
    public ApiKeyResponse create(ApiKeyCreateRequest req) {
        User currentUser = getCurrentUser();
        long existingCount = apiKeyRepository.countByCreatedByIdAndIsActiveTrue(currentUser.getId());
        if (existingCount >= 10) {
            throw new IllegalArgumentException("Maximum 10 active API keys allowed per merchant");
        }
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
        return apiKeyRepository.findAll(withOwnerFilter(spec), pageable)
                .map(ApiKeyResponse::from);
    }

    @Transactional(readOnly = true)
    public ApiKeyResponse getById(Long id) {
        return ApiKeyResponse.from(findById(id));
    }

    @Transactional
    public ApiKeyResponse updateRateLimit(Long id, RateLimitUpdateRequest request) {
        ApiKey apiKey = findById(id);
        if (request.getRateLimitPerMinute() != null) apiKey.setRateLimitPerMinute(request.getRateLimitPerMinute());
        if (request.getRateLimitPerDay() != null) apiKey.setRateLimitPerDay(request.getRateLimitPerDay());
        return ApiKeyResponse.from(apiKeyRepository.save(apiKey));
    }

    @Transactional(readOnly = true)
    public ApiKeyUsageResponse getUsage(Long id) {
        ApiKey apiKey = findById(id);
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
        ApiKey apiKey = findById(id);
        apiKey.setIsActive(false);
        ApiKeyResponse result = ApiKeyResponse.from(apiKeyRepository.save(apiKey));
        auditLogService.log("DEACTIVATE", "ApiKey", id, null, null);
        return result;
    }

    private ApiKey findById(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));
        checkOwnership(apiKey);
        return apiKey;
    }

    private void checkOwnership(ApiKey apiKey) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)
                && !apiKey.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("API key not found: " + apiKey.getId());
        }
    }

    private Specification<ApiKey> withOwnerFilter(Specification<ApiKey> spec) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)) {
            User owner = currentUser;
            Specification<ApiKey> ownerSpec = (root, query, cb) -> cb.equal(root.get("createdBy"), owner);
            return spec == null ? ownerSpec : spec.and(ownerSpec);
        }
        return spec != null ? spec : Specification.where(null);
    }

    private boolean isRestricted(User user) {
        return user.getRole() == UserRole.STAFF || user.getRole() == UserRole.USER;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
