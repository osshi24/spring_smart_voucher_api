package com.smartvoucher.repository;

import com.smartvoucher.entity.VoucherCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherCodeRepository extends JpaRepository<VoucherCode, Long> {
    Optional<VoucherCode> findByCode(String code);
    Optional<VoucherCode> findByCodeIgnoreCase(String code);
    long countByVoucherId(Long voucherId);
    List<VoucherCode> findByVoucherIdAndCustomerIsNull(Long voucherId);
    Optional<VoucherCode> findByVoucherIdAndCustomerId(Long voucherId, Long customerId);

    @Query("SELECT vc FROM VoucherCode vc WHERE vc.voucher.id = :voucherId AND vc.customer IS NULL ORDER BY vc.id ASC")
    List<VoucherCode> findUnassignedByVoucherId(@Param("voucherId") Long voucherId);

    Page<VoucherCode> findByVoucherId(Long voucherId, Pageable pageable);
}
