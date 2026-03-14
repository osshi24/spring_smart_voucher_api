package com.smartvoucher.controller;

import com.smartvoucher.dto.request.UserUpdateRequest;
import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.UserResponse;
import com.smartvoucher.entity.User;
import com.smartvoucher.service.PasswordResetService;
import com.smartvoucher.service.UserRegistrationService;
import com.smartvoucher.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.And;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Quản lý người dùng", description = "Admin quản lý tài khoản người dùng trong hệ thống")
public class UserController {

    private final UserService userService;
    private final UserRegistrationService userRegistrationService;
    private final PasswordResetService passwordResetService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Lấy danh sách người dùng (có bộ lọc)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAll(
            @And({
                @Spec(spec = Equal.class,              params = "id",             path = "id"),
                @Spec(spec = Like.class,               params = "username",       path = "username"),
                @Spec(spec = Like.class,               params = "email",          path = "email"),
                @Spec(spec = Like.class,               params = "fullName",       path = "fullName"),
                @Spec(spec = Like.class,               params = "phone",          path = "phone"),
                @Spec(spec = Equal.class,              params = "role",           path = "role"),
                @Spec(spec = Equal.class,              params = "status",         path = "status"),
                @Spec(spec = IsTrue.class,             params = "isActive",       path = "isActive"),
                @Spec(spec = Equal.class,              params = "emailVerified",  path = "emailVerified"),
                @Spec(spec = GreaterThanOrEqual.class, params = "createdAtFrom",  path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "createdAtTo",    path = "createdAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = GreaterThanOrEqual.class, params = "updatedAtFrom",  path = "updatedAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX"),
                @Spec(spec = LessThanOrEqual.class,    params = "updatedAtTo",    path = "updatedAt",
                        config = "yyyy-MM-dd'T'HH:mm:ssXXX")
            }) Specification<User> spec,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAll(spec, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Lấy chi tiết thông tin người dùng theo ID")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Cập nhật thông tin người dùng (họ tên, email, vai trò)")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(id, request)));
    }

    @Operation(summary = "Duyệt tài khoản đăng ký đang chờ phê duyệt")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('USER_APPROVE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(@PathVariable Long id) {
        userRegistrationService.approveUser(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "User approved successfully.", "userId", id)));
    }

    @Operation(summary = "Từ chối đăng ký tài khoản")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('USER_REJECT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(@PathVariable Long id) {
        userRegistrationService.rejectUser(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "User rejected.", "userId", id)));
    }

    @Operation(summary = "Admin đặt lại mật khẩu tạm thời và gửi qua email cho người dùng")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE')")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(@PathVariable Long id) {
        passwordResetService.adminResetPassword(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Temporary password sent to user's email.")));
    }
}
