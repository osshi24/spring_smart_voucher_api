package com.smartvoucher.security;

import com.smartvoucher.entity.ApiKey;
import com.smartvoucher.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (StringUtils.hasText(apiKey) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                List<ApiKey> activeKeys = apiKeyRepository.findByIsActiveTrue();
                for (ApiKey key : activeKeys) {
                    if (passwordEncoder.matches(apiKey, key.getKeyHash())) {
                        if (key.getExpiresAt() == null || key.getExpiresAt().isAfter(OffsetDateTime.now())) {
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    "apikey:" + key.getSystemName(),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_API_KEY"))
                            );
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            request.setAttribute("apiKeyId", key.getId());
                        } else {
                            log.warn("API key '{}' matched but is expired (expiresAt={})",
                                    key.getSystemName(), key.getExpiresAt());
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("API key authentication error: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
