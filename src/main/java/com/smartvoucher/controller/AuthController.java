package com.smartvoucher.controller;

import com.smartvoucher.dto.request.*;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.LoginResponse;
import com.smartvoucher.dto.response.UserResponse;
import com.smartvoucher.entity.User;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.UserRepository;
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
@Tag(name = "Xác thực", description = "Đăng nhập, đăng ký tài khoản và quản lý mật khẩu")
public class AuthController {

    private final AuthService authService;
    private final UserRegistrationService userRegistrationService;
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin người dùng đang đăng nhập")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng username và mật khẩu")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới JWT access token bằng refresh token")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@RequestParam String refreshToken) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(refreshToken)));
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản nhân viên (STAFF) mới")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of(
                        "message", "Registration successful. Please check your email to verify your account.",
                        "userId", userId)));
    }

    @PostMapping("/register-merchant")
    @Operation(summary = "Đăng ký tài khoản merchant có quyền dùng API key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerMerchant(@Valid @RequestBody RegisterRequest request) {
        Long userId = userRegistrationService.registerMerchant(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of(
                        "message", "Merchant registration successful. Please check your email to verify your account.",
                        "userId", userId)));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Xác thực email bằng mã OTP")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmail(
            @Valid @RequestBody VerifyEmailOtpRequest request) {
        userRegistrationService.verifyEmail(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Email verified successfully.")));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Gửi lại email xác thực")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendVerification(
            @Valid @RequestBody ForgotPasswordRequest request) {
        userRegistrationService.resendVerification(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Verification email sent.")));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Yêu cầu mã OTP để đặt lại mật khẩu qua email")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPasswordOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message", "If this email is registered, an OTP has been sent.")));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Xác thực OTP và nhận reset token để đặt lại mật khẩu")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        String resetToken = passwordResetService.verifyOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success(Map.of("resetToken", resetToken)));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu bằng reset token")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPasswordByOtp(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Password reset successfully.")));
    }

    @PutMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu cho người dùng hiện tại")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwordResetService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Password changed successfully.")));
    }
}
