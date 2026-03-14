package com.smartvoucher.filter;

import com.smartvoucher.service.ApiRequestLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestTrackingFilter extends OncePerRequestFilter {

    private final ApiRequestLogService apiRequestLogService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            Long apiKeyId = (Long) request.getAttribute("apiKeyId");
            if (apiKeyId != null) {
                apiRequestLogService.logRequest(
                        request.getRequestURI(),
                        request.getMethod(),
                        null,
                        response.getStatus(),
                        null,
                        request.getRemoteAddr(),
                        apiKeyId
                );
            }
        }
    }
}
