package com.smartvoucher.service;

import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final RolePermissionRepository rolePermissionRepository;

    @Cacheable(value = "rolePermissions", key = "#role.name()")
    public Set<String> getPermissionNamesForRole(UserRole role) {
        return rolePermissionRepository.findPermissionNamesByRole(role);
    }

    @CacheEvict(value = "rolePermissions", key = "#role.name()")
    public void evictPermissionCache(UserRole role) {
        // Cache evicted by annotation
    }
}
