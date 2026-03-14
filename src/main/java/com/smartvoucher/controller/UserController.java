package com.smartvoucher.controller;

import com.smartvoucher.dto.request.UserUpdateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.UserResponse;
import com.smartvoucher.entity.enums.UserStatus;
import com.smartvoucher.service.PasswordResetService;
import com.smartvoucher.service.UserRegistrationService;
import com.smartvoucher.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Admin endpoints for managing user accounts")
public class UserController {

    private final UserService userService;
    private final UserRegistrationService userRegistrationService;
    private final PasswordResetService passwordResetService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAll(
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAll(status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Update user fullName, email and role")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(id, request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('USER_APPROVE')")
    @Operation(summary = "Approve a pending user account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(@PathVariable Long id) {
        userRegistrationService.approveUser(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "User approved successfully.", "userId", id)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('USER_REJECT')")
    @Operation(summary = "Reject a pending user account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(@PathVariable Long id) {
        userRegistrationService.rejectUser(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "User rejected.", "userId", id)));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE')")
    @Operation(summary = "Admin reset password for a user")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(@PathVariable Long id) {
        passwordResetService.adminResetPassword(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Temporary password sent to user's email.")));
    }
}
