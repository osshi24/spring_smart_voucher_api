package com.smartvoucher.controller;

import com.smartvoucher.dto.request.LoginRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.LoginResponse;
import com.smartvoucher.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@RequestParam String refreshToken) {
        LoginResponse response = authService.refresh(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
