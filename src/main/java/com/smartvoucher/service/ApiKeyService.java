package com.smartvoucher.service;

import com.smartvoucher.dto.request.ApiKeyCreateRequest;
import com.smartvoucher.dto.response.ApiKeyResponse;
import com.smartvoucher.entity.ApiKey;
import com.smartvoucher.entity.User;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.ApiKeyRepository;
import com.smartvoucher.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ApiKeyResponse create(ApiKeyCreateRequest req) {
        User currentUser = getCurrentUser();

        // Generate random API key with prefix
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
    public List<ApiKeyResponse> getAll() {
        return apiKeyRepository.findAll().stream()
                .map(ApiKeyResponse::from)
                .toList();
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
