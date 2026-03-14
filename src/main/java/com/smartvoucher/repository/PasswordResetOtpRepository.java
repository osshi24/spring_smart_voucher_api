package com.smartvoucher.repository;

import com.smartvoucher.entity.PasswordResetOtp;
import com.smartvoucher.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findTopByUserOrderByCreatedAtDesc(User user);

    Optional<PasswordResetOtp> findByResetToken(String resetToken);

    @Modifying
    @Query("DELETE FROM PasswordResetOtp o WHERE o.user = :user")
    void deleteByUser(User user);
}
