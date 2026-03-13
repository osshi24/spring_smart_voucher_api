package com.smartvoucher.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoucher.entity.ApiKey;
import com.smartvoucher.repository.ApiKeyRepository;
import com.smartvoucher.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.default-per-minute:100}")
    private int defaultLimitPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long apiKeyId = (Long) request.getAttribute("apiKeyId");

        if (apiKeyId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = defaultLimitPerMinute;
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(apiKeyId);
        if (apiKeyOpt.isPresent() && apiKeyOpt.get().getRateLimitPerMinute() != null) {
            limit = apiKeyOpt.get().getRateLimitPerMinute();
        }

        RateLimitService.RateLimitResult result = rateLimitService.checkAndIncrement(apiKeyId, limit);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetEpochSeconds()));

        if (result.isExceeded()) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> body = Map.of(
                    "success", false,
                    "error", Map.of(
                            "code", "RATE_LIMIT_EXCEEDED",
                            "message", "Rate limit exceeded. Try again after the current window resets.",
                            "details", "Limit: " + result.limit() + " req/min"
                    )
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
