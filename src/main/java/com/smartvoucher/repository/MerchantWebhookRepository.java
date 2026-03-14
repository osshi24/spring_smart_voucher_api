package com.smartvoucher.repository;

import com.smartvoucher.entity.MerchantWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerchantWebhookRepository extends JpaRepository<MerchantWebhook, Long> {
    List<MerchantWebhook> findByUserIdAndIsActiveTrue(Long userId);
    List<MerchantWebhook> findByUserId(Long userId);
}
