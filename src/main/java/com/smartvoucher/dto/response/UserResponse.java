package com.smartvoucher.dto.response;

import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.entity.enums.UserStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private UserRole role;
    private UserStatus status;
    private Boolean isActive;
    private OffsetDateTime createdAt;

    public static UserResponse from(User user) {
        UserResponse res = new UserResponse();
        res.id = user.getId();
        res.username = user.getUsername();
        res.email = user.getEmail();
        res.fullName = user.getFullName();
        res.phone = user.getPhone();
        res.role = user.getRole();
        res.status = user.getStatus();
        res.isActive = user.getIsActive();
        res.createdAt = user.getCreatedAt();
        return res;
    }
}
