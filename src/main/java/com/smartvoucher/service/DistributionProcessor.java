package com.smartvoucher.service;

import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherCustomer;
import com.smartvoucher.entity.VoucherDistribution;
import com.smartvoucher.entity.enums.DistributionChannel;
import com.smartvoucher.entity.enums.DistributionStatus;
import com.smartvoucher.repository.DistributionRepository;
import com.smartvoucher.repository.VoucherCodeRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DistributionProcessor {

    private final DistributionRepository distributionRepository;
    private final VoucherCodeRepository voucherCodeRepository;
    private final VoucherCustomerRepository voucherCustomerRepository;
    private final EmailService emailService;
    private final QrTokenService qrTokenService;

    @Async("distributionTaskExecutor")
    @Transactional
    public void processAsync(Long distributionId) {
        if (distributionId == null) return;
        doProcess(distributionId);
    }

    @Transactional
    public void processSync(Long distributionId) {
        if (distributionId == null) return;
        doProcess(distributionId);
    }

    private void doProcess(Long distributionId) {
        VoucherDistribution dist = distributionRepository.findById(distributionId).orElse(null);
        if (dist == null) return;
        try {
            Voucher voucher = dist.getVoucher();
            Customer customer = dist.getCustomer();
            log.info("Sending voucher {} via {} to customer {}",
                    voucher.getCode(), dist.getChannel(), customer.getFullName());

            // Code delivered to the customer: assigned child code for UNIQUE, master code for SHARED
            String deliveredCode = voucher.getCode();

            if (voucher.getCodeType() == com.smartvoucher.entity.enums.CodeType.UNIQUE) {
                com.smartvoucher.entity.VoucherCode assigned = voucherCodeRepository
                        .findByVoucherIdAndCustomerId(voucher.getId(), customer.getId())
                        .orElse(null);
                if (assigned == null) {
                    java.util.List<com.smartvoucher.entity.VoucherCode> unassigned =
                            voucherCodeRepository.findUnassignedByVoucherId(voucher.getId());
                    if (unassigned.isEmpty()) {
                        throw new IllegalStateException("No unassigned unique codes available for voucher " + voucher.getCode());
                    }
                    assigned = unassigned.get(0);
                    assigned.setCustomer(customer);
                    voucherCodeRepository.save(assigned);
                }
                deliveredCode = assigned.getCode();
                // Sync to voucher_customers so validate()'s private-voucher check works correctly
                if (!voucherCustomerRepository.existsByVoucherIdAndCustomerId(voucher.getId(), customer.getId())) {
                    voucherCustomerRepository.save(VoucherCustomer.builder()
                            .voucher(voucher)
                            .customer(customer)
                            .build());
                }
            }

            if (dist.getChannel() == DistributionChannel.EMAIL && customer.getEmail() != null) {
                Long merchantId = voucher.getCreatedBy() != null ? voucher.getCreatedBy().getId() : null;
                String token = qrTokenService.generateQrToken(deliveredCode, customer.getId(), merchantId);
                String qrUrl = qrTokenService.buildQrUrl(token);
                byte[] qrImage = qrTokenService.generateQrImage(qrUrl);
                emailService.sendVoucherEmail(customer.getEmail(), voucher, deliveredCode, customer.getFullName(), qrImage);
            }

            dist.setStatus(DistributionStatus.SENT);
            dist.setSentAt(OffsetDateTime.now());
        } catch (Exception e) {
            log.warn("Distribution {} failed: {}", distributionId, e.getMessage());
            dist.setStatus(DistributionStatus.FAILED);
            dist.setErrorMessage(e.getMessage());
            dist.setFailureReason(e.getMessage());
        }
        distributionRepository.save(dist);
    }
}
