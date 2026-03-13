package com.smartvoucher.repository;

import com.smartvoucher.entity.RolePermission;
import com.smartvoucher.entity.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.RolePermissionId> {

    List<RolePermission> findByRole(UserRole role);

    @Query("SELECT rp.permission.name FROM RolePermission rp WHERE rp.role = :role")
    Set<String> findPermissionNamesByRole(@Param("role") UserRole role);

    boolean existsByRoleAndPermissionId(UserRole role, Long permissionId);

    void deleteByRoleAndPermissionId(UserRole role, Long permissionId);
}
