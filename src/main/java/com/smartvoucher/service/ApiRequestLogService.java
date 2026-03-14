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

    public Page<ApiRequestLog> findLogs(Long id, Long apiKeyId, String method, String endpoint,
                                         Integer responseStatus, String ipAddress,
                                         Long responseTimeMsMin, Long responseTimeMsMax,
                                         OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        Specification<ApiRequestLog> spec = Specification.where(null);
        if (id != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("id"), id));
        if (apiKeyId != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("apiKey").get("id"), apiKeyId));
        if (method != null && !method.isBlank())
            spec = spec.and((root, q, cb) -> cb.equal(root.get("method"), method.toUpperCase()));
        if (endpoint != null && !endpoint.isBlank())
            spec = spec.and((root, q, cb) -> cb.like(root.get("endpoint"), "%" + endpoint + "%"));
        if (responseStatus != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("responseStatus"), responseStatus));
        if (ipAddress != null && !ipAddress.isBlank())
            spec = spec.and((root, q, cb) -> cb.like(root.get("ipAddress"), "%" + ipAddress + "%"));
        if (responseTimeMsMin != null)
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("responseTimeMs"), responseTimeMsMin));
        if (responseTimeMsMax != null)
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("responseTimeMs"), responseTimeMsMax));
        if (from != null)
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null)
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
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
            if (apiKeyId != null) logEntry.setApiKeyId(apiKeyId);
            apiRequestLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to log API request: {}", e.getMessage());
        }
    }
}
