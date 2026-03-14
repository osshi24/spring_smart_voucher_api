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

    @Value("${app.rate-limit.default-per-day:0}")
    private int defaultLimitPerDay;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long apiKeyId = (Long) request.getAttribute("apiKeyId");

        if (apiKeyId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int minuteLimit = defaultLimitPerMinute;
        int dayLimit = defaultLimitPerDay;

        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(apiKeyId);
        if (apiKeyOpt.isPresent()) {
            ApiKey apiKey = apiKeyOpt.get();
            if (apiKey.getRateLimitPerMinute() != null) minuteLimit = apiKey.getRateLimitPerMinute();
            if (apiKey.getRateLimitPerDay() != null) dayLimit = apiKey.getRateLimitPerDay();
        }

        // Check per-minute limit
        RateLimitService.RateLimitResult minuteResult = rateLimitService.checkAndIncrementMinute(apiKeyId, minuteLimit);
        response.setHeader("X-RateLimit-Limit-Minute", String.valueOf(minuteResult.limit()));
        response.setHeader("X-RateLimit-Remaining-Minute", String.valueOf(minuteResult.remaining()));
        response.setHeader("X-RateLimit-Reset-Minute", String.valueOf(minuteResult.resetEpochSeconds()));

        if (minuteResult.isExceeded()) {
            writeRateLimitError(response, "RATE_LIMIT_EXCEEDED",
                    "Rate limit exceeded. Limit: " + minuteResult.limit() + " req/min");
            return;
        }

        // Check per-day limit (0 = unlimited)
        if (dayLimit > 0) {
            RateLimitService.RateLimitResult dayResult = rateLimitService.checkAndIncrementDay(apiKeyId, dayLimit);
            response.setHeader("X-RateLimit-Limit-Day", String.valueOf(dayResult.limit()));
            response.setHeader("X-RateLimit-Remaining-Day", String.valueOf(dayResult.remaining()));
            response.setHeader("X-RateLimit-Reset-Day", String.valueOf(dayResult.resetEpochSeconds()));

            if (dayResult.isExceeded()) {
                writeRateLimitError(response, "DAILY_LIMIT_EXCEEDED",
                        "Daily request limit exceeded. Limit: " + dayResult.limit() + " req/day");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeRateLimitError(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of("code", code, "message", message)
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
