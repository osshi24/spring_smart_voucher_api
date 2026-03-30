package com.smartvoucher.service;

import com.smartvoucher.entity.AuditLog;
import com.smartvoucher.entity.User;
import com.smartvoucher.repository.AuditLogRepository;
import com.smartvoucher.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public void log(String action, String entityType, Long entityId, Object oldValue, Object newValue) {
        try {
            User user = null;
            try {
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                user = userRepository.findByUsername(username).orElse(null);
            } catch (Exception ignored) {}

            AuditLog entry = AuditLog.builder()
                    .user(user)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<com.smartvoucher.dto.response.AuditLogResponse> getAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(com.smartvoucher.dto.response.AuditLogResponse::from);
    }
}
