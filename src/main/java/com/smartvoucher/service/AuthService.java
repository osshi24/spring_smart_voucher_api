package com.smartvoucher.service;

import com.smartvoucher.annotation.Auditable;
import com.smartvoucher.config.JwtConfig;
import com.smartvoucher.dto.request.LoginRequest;
import com.smartvoucher.dto.response.LoginResponse;
import com.smartvoucher.entity.User;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.exception.UnauthorizedException;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final UserRepository userRepository;

    @Auditable(action = "LOGIN", entityType = "User")
    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(), user.getUsername(), user.getRole().name()
            );
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

            return new LoginResponse(accessToken, refreshToken, jwtConfig.getAccessExpiration() / 1000);
        } catch (BadCredentialsException e) {
            throw e;
        }
    }

    public LoginResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name()
        );
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return new LoginResponse(newAccessToken, newRefreshToken, jwtConfig.getAccessExpiration() / 1000);
    }
}
