package com.smartvoucher.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartvoucher.dto.request.LoginRequest;
import com.smartvoucher.dto.response.LoginResponse;
import com.smartvoucher.exception.GlobalExceptionHandler;
import com.smartvoucher.exception.UnauthorizedException;
import com.smartvoucher.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_validCredentials_returns200() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin01");
        req.setPassword("admin123");

        LoginResponse loginResponse = new LoginResponse("access-token", "refresh-token", 3600L);
        when(authService.login(any())).thenReturn(loginResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin01");
        req.setPassword("wrongpass");

        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void refresh_validToken_returns200() throws Exception {
        LoginResponse loginResponse = new LoginResponse("new-access", "new-refresh", 3600L);
        when(authService.refresh("valid-token")).thenReturn(loginResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .param("refreshToken", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));
    }

    @Test
    void refresh_expiredToken_returns401() throws Exception {
        when(authService.refresh("expired-token"))
                .thenThrow(new UnauthorizedException("Invalid or expired refresh token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .param("refreshToken", "expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
