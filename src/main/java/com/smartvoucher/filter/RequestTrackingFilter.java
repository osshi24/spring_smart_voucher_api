package com.smartvoucher.filter;

import com.smartvoucher.entity.ApiRequestLog;
import com.smartvoucher.repository.ApiRequestLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestTrackingFilter extends OncePerRequestFilter {

    private final ApiRequestLogRepository apiRequestLogRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            Long apiKeyId = (Long) request.getAttribute("apiKeyId");
            if (apiKeyId != null) {
                long duration = System.currentTimeMillis() - startTime;
                saveLogAsync(apiKeyId, request.getMethod(), request.getRequestURI(),
                        response.getStatus(), duration);
            }
        }
    }

    @Async("emailTaskExecutor")
    public void saveLogAsync(Long apiKeyId, String method, String path, int status, long durationMs) {
        try {
            ApiRequestLog log = ApiRequestLog.builder()
                    .method(method)
                    .endpoint(path)
                    .responseStatus(status)
                    .responseTimeMs(durationMs)
                    .build();
            log.setApiKeyId(apiKeyId);
            apiRequestLogRepository.save(log);
        } catch (Exception e) {
            logger.error("Failed to save request log: " + e.getMessage());
        }
    }
}
