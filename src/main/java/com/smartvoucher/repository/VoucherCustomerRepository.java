package com.smartvoucher.repository;

import com.smartvoucher.entity.VoucherCustomer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherCustomerRepository extends JpaRepository<VoucherCustomer, Long>, JpaSpecificationExecutor<VoucherCustomer> {
    boolean existsByVoucherIdAndCustomerId(Long voucherId, Long customerId);
    List<VoucherCustomer> findByVoucherId(Long voucherId);
    Page<VoucherCustomer> findByVoucherId(Long voucherId, Pageable pageable);
    Page<VoucherCustomer> findByCustomerId(Long customerId, Pageable pageable);
    Optional<VoucherCustomer> findByVoucherIdAndCustomerId(Long voucherId, Long customerId);
    void deleteByVoucherIdAndCustomerId(Long voucherId, Long customerId);

    @Query("SELECT vc FROM VoucherCustomer vc JOIN FETCH vc.voucher v WHERE vc.customer.id = :customerId AND v.status = 'ACTIVE' AND v.validFrom <= :now AND v.validUntil >= :now")
    List<VoucherCustomer> findActiveVouchersForCustomer(@Param("customerId") Long customerId, @Param("now") OffsetDateTime now);
}
