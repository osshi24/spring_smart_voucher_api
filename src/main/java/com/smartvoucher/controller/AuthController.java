package com.smartvoucher.controller;

import com.smartvoucher.dto.request.*;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.LoginResponse;
import com.smartvoucher.service.AuthService;
import com.smartvoucher.service.PasswordResetService;
import com.smartvoucher.service.UserRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Auth and account management endpoints")
public class AuthController {

    private final AuthService authService;
    private final UserRegistrationService userRegistrationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    @Operation(summary = "Login with username and password")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@RequestParam String refreshToken) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(refreshToken)));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new staff account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of(
                        "message", "Registration successful. Please check your email to verify your account.",
                        "userId", userId)));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with token")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmail(@RequestParam String token) {
        userRegistrationService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Email verified successfully.")));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification link")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendVerification(
            @AuthenticationPrincipal UserDetails userDetails) {
        userRegistrationService.resendVerification(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Verification email sent.")));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset link via email")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message", "If this email is registered, a reset link has been sent.")));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using token")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Password reset successfully.")));
    }

    @PutMapping("/change-password")
    @Operation(summary = "Change password for authenticated user")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwordResetService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Password changed successfully.")));
    }
}
