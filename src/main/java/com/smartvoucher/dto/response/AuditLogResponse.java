package com.smartvoucher.dto.response;

import com.smartvoucher.entity.AuditLog;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String entityType;
    private Long entityId;
    private Object oldValue;
    private Object newValue;
    private OffsetDateTime createdAt;

    public static AuditLogResponse from(AuditLog log) {
        AuditLogResponse res = new AuditLogResponse();
        res.id = log.getId();
        if (log.getUser() != null) {
            res.userId = log.getUser().getId();
            res.username = log.getUser().getUsername();
        }
        res.action = log.getAction();
        res.entityType = log.getEntityType();
        res.entityId = log.getEntityId();
        res.oldValue = log.getOldValue();
        res.newValue = log.getNewValue();
        res.createdAt = log.getCreatedAt();
        return res;
    }
}
