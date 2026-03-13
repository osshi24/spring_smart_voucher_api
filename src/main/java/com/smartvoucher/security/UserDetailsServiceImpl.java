package com.smartvoucher.security;

import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.entity.enums.UserStatus;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // REJECTED users cannot log in
        boolean enabled = Boolean.TRUE.equals(user.getIsActive()) && user.getStatus() != UserStatus.REJECTED;

        // PENDING users get USER-role permissions only (limited access)
        UserRole effectiveRole = user.getStatus() == UserStatus.PENDING
                ? UserRole.USER
                : user.getRole();

        Set<String> permissionNames = permissionService.getPermissionNamesForRole(effectiveRole);

        var authorities = permissionNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                enabled,
                true,
                true,
                true,
                authorities
        );
    }
}
