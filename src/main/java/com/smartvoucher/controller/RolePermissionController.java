package com.smartvoucher.controller;

import com.smartvoucher.dto.response.ApiResponse;
import com.smartvoucher.dto.response.PermissionResponse;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Phân quyền", description = "Quản lý gán/thu hồi quyền cho các vai trò trong hệ thống")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE')")
    @Operation(summary = "Lấy danh sách tất cả quyền trong hệ thống")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        List<PermissionResponse> result = rolePermissionService.getAllPermissions().stream()
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/roles/{role}/permissions")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE')")
    @Operation(summary = "Lấy danh sách quyền được gán cho một vai trò")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRolePermissions(@PathVariable UserRole role) {
        List<PermissionResponse> permissions = rolePermissionService.getPermissionsForRole(role).stream()
                .map(rp -> PermissionResponse.builder()
                        .id(rp.getPermission().getId())
                        .name(rp.getPermission().getName())
                        .description(rp.getPermission().getDescription())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(Map.of("role", role, "permissions", permissions)));
    }

    @PostMapping("/roles/{role}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE')")
    @Operation(summary = "Gán quyền cho vai trò")
    public ResponseEntity<ApiResponse<String>> assignPermission(
            @PathVariable UserRole role,
            @PathVariable Long permissionId) {
        rolePermissionService.assignPermission(role, permissionId);
        return ResponseEntity.ok(ApiResponse.success("Permission assigned."));
    }

    @DeleteMapping("/roles/{role}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE')")
    @Operation(summary = "Thu hồi quyền khỏi vai trò")
    public ResponseEntity<ApiResponse<String>> revokePermission(
            @PathVariable UserRole role,
            @PathVariable Long permissionId) {
        rolePermissionService.revokePermission(role, permissionId);
        return ResponseEntity.ok(ApiResponse.success("Permission revoked."));
    }
}
