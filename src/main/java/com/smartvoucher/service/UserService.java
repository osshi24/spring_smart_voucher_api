package com.smartvoucher.service;

import com.smartvoucher.dto.request.UserUpdateRequest;
import com.smartvoucher.dto.response.UserResponse;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserStatus;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        // Check email uniqueness for other users
        userRepository.findByEmail(req.getEmail()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateResourceException("Email already in use: " + req.getEmail());
            }
        });

        user.setFullName(req.getFullName());
        user.setEmail(req.getEmail());
        user.setRole(req.getRole());

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAll(UserStatus status, Pageable pageable) {
        Page<User> page = status != null
                ? userRepository.findByStatus(status, pageable)
                : userRepository.findAll(pageable);
        return page.map(UserResponse::from);
    }
}
