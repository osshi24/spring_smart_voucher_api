package com.smartvoucher.repository;

import com.smartvoucher.entity.VoucherDistribution;
import com.smartvoucher.entity.enums.DistributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DistributionRepository extends JpaRepository<VoucherDistribution, Long> {
    List<VoucherDistribution> findByVoucherId(Long voucherId);
    List<VoucherDistribution> findByStatus(DistributionStatus status);
    List<VoucherDistribution> findByCustomerId(Long customerId);
}
