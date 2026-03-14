package com.smartvoucher.repository;

import com.smartvoucher.entity.EmailVerificationOtp;
import com.smartvoucher.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmailVerificationOtpRepository extends JpaRepository<EmailVerificationOtp, Long> {

    Optional<EmailVerificationOtp> findTopByUserOrderByCreatedAtDesc(User user);

    @Modifying
    @Query("DELETE FROM EmailVerificationOtp o WHERE o.user = :user")
    void deleteByUser(User user);
}
