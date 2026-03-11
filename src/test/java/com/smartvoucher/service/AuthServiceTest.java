package com.smartvoucher.service;

import com.smartvoucher.config.JwtConfig;
import com.smartvoucher.dto.request.LoginRequest;
import com.smartvoucher.dto.response.LoginResponse;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.exception.UnauthorizedException;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtConfig jwtConfig;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setUsername("admin01");
        admin.setRole(UserRole.ADMIN);
        admin.setIsActive(true);
    }

    @Test
    void login_validCredentials_returnsTokens() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin01");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findByUsername("admin01")).thenReturn(Optional.of(admin));
        when(jwtTokenProvider.generateAccessToken(1L, "admin01", "ADMIN")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtConfig.getAccessExpiration()).thenReturn(3600000L);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin01");
        req.setPassword("admin123");

        LoginResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
    }

    @Test
    void login_invalidCredentials_throws() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest req = new LoginRequest();
        req.setUsername("admin01");
        req.setPassword("wrongpass");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_validToken_returnsNewTokens() {
        when(jwtTokenProvider.validateToken("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-refresh")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(jwtTokenProvider.generateAccessToken(1L, "admin01", "ADMIN")).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("new-refresh");
        when(jwtConfig.getAccessExpiration()).thenReturn(3600000L);

        LoginResponse response = authService.refresh("valid-refresh");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refresh_invalidToken_throws() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
