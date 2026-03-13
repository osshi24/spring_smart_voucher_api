package com.smartvoucher.service;

import com.smartvoucher.entity.ApiRequestLog;
import com.smartvoucher.repository.ApiRequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiRequestLogService {

    private final ApiRequestLogRepository apiRequestLogRepository;

    public Page<ApiRequestLog> findLogs(Long apiKeyId, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        Specification<ApiRequestLog> spec = Specification.where(null);
        if (apiKeyId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("apiKey").get("id"), apiKeyId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return apiRequestLogRepository.findAll(spec, pageable);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRequest(String endpoint, String method, Object requestBody,
                           int responseStatus, Object responseBody,
                           String ipAddress, Long apiKeyId) {
        try {
            ApiRequestLog logEntry = new ApiRequestLog();
            logEntry.setEndpoint(endpoint);
            logEntry.setMethod(method);
            logEntry.setRequestBody(requestBody);
            logEntry.setResponseStatus(responseStatus);
            logEntry.setResponseBody(responseBody);
            logEntry.setIpAddress(ipAddress);
            if (apiKeyId != null) {
                // We'll set apiKeyId via a simple holder
                logEntry.setApiKeyId(apiKeyId);
            }
            apiRequestLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to log API request: {}", e.getMessage());
        }
    }
}
