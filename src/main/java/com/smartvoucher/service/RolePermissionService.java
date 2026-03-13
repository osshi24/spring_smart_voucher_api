package com.smartvoucher.service;

import com.smartvoucher.entity.Permission;
import com.smartvoucher.entity.RolePermission;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.PermissionRepository;
import com.smartvoucher.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionService permissionService;

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    public List<RolePermission> getPermissionsForRole(UserRole role) {
        return rolePermissionRepository.findByRole(role);
    }

    @Transactional
    public void assignPermission(UserRole role, Long permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));

        if (rolePermissionRepository.existsByRoleAndPermissionId(role, permissionId)) {
            throw new DuplicateResourceException("Permission already assigned to role " + role);
        }

        RolePermission rp = RolePermission.builder()
                .role(role)
                .permission(permission)
                .build();
        rolePermissionRepository.save(rp);
        permissionService.evictPermissionCache(role);
    }

    @Transactional
    public void revokePermission(UserRole role, Long permissionId) {
        if (!rolePermissionRepository.existsByRoleAndPermissionId(role, permissionId)) {
            throw new ResourceNotFoundException("Permission not assigned to role " + role);
        }
        rolePermissionRepository.deleteByRoleAndPermissionId(role, permissionId);
        permissionService.evictPermissionCache(role);
    }
}
