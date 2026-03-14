package com.smartvoucher.repository;

import com.smartvoucher.entity.VoucherUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {

    boolean existsByVoucherIdAndExternalOrderId(Long voucherId, String externalOrderId);

    boolean existsByVoucherIdAndCustomerId(Long voucherId, Long customerId);

    long countByVoucherIdAndCustomerId(Long voucherId, Long customerId);

    long countByVoucherId(Long voucherId);

    List<VoucherUsage> findByVoucherId(Long voucherId);

    Page<VoucherUsage> findByVoucherId(Long voucherId, Pageable pageable);

    Page<VoucherUsage> findByCustomerId(Long customerId, Pageable pageable);

    @Query("SELECT SUM(u.discountAmount) FROM VoucherUsage u WHERE u.voucher.campaign.id = :campaignId")
    java.math.BigDecimal sumDiscountAmountByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT COUNT(u) FROM VoucherUsage u WHERE u.voucher.campaign.id = :campaignId")
    long countByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT u FROM VoucherUsage u WHERE u.usedAt BETWEEN :from AND :to ORDER BY u.usedAt")
    List<VoucherUsage> findByUsedAtBetween(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
