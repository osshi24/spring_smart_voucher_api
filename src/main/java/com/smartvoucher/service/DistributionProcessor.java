package com.smartvoucher.service;

import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherDistribution;
import com.smartvoucher.entity.enums.DistributionChannel;
import com.smartvoucher.entity.enums.DistributionStatus;
import com.smartvoucher.repository.DistributionRepository;
import com.smartvoucher.repository.VoucherCodeRepository;
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

            if (voucher.getCodeType() == com.smartvoucher.entity.enums.CodeType.UNIQUE) {
                boolean alreadyAssigned = voucherCodeRepository
                        .findByVoucherIdAndCustomerId(voucher.getId(), customer.getId()).isPresent();
                if (!alreadyAssigned) {
                    java.util.List<com.smartvoucher.entity.VoucherCode> unassigned =
                            voucherCodeRepository.findUnassignedByVoucherId(voucher.getId());
                    if (unassigned.isEmpty()) {
                        throw new IllegalStateException("No unassigned unique codes available for voucher " + voucher.getCode());
                    }
                    com.smartvoucher.entity.VoucherCode code = unassigned.get(0);
                    code.setCustomer(customer);
                    voucherCodeRepository.save(code);
                }
            }

            if (dist.getChannel() == DistributionChannel.EMAIL && customer.getEmail() != null) {
                Long merchantId = voucher.getCreatedBy() != null ? voucher.getCreatedBy().getId() : null;
                String token = qrTokenService.generateQrToken(voucher.getCode(), customer.getId(), merchantId);
                String qrUrl = qrTokenService.buildQrUrl(token);
                byte[] qrImage = qrTokenService.generateQrImage(qrUrl);
                emailService.sendVoucherEmail(customer.getEmail(), voucher, customer.getFullName(), qrImage);
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
