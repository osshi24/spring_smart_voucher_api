package com.smartvoucher.service;

import com.smartvoucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoucherExpiryScheduler {

    private final VoucherRepository voucherRepository;

    @Scheduled(cron = "${scheduler.voucher-expiry.cron:0 0 1 * * ?}")
    @Transactional
    public void expireOverdueVouchers() {
        int count = voucherRepository.bulkExpireOverdue();
        if (count > 0) {
            log.info("Expired {} overdue vouchers", count);
        }
    }
}
