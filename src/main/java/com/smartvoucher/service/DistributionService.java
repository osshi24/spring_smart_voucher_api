package com.smartvoucher.service;

import com.smartvoucher.dto.request.DistributionCreateRequest;
import com.smartvoucher.dto.response.DistributionResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherDistribution;
import com.smartvoucher.entity.enums.DistributionStatus;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.DistributionRepository;
import com.smartvoucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributionService {

    private final DistributionRepository distributionRepository;
    private final VoucherRepository voucherRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public DistributionResponse create(DistributionCreateRequest req) {
        Voucher voucher = voucherRepository.findById(req.getVoucherId())
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + req.getVoucherId()));
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + req.getCustomerId()));

        VoucherDistribution distribution = new VoucherDistribution();
        distribution.setVoucher(voucher);
        distribution.setCustomer(customer);
        distribution.setChannel(req.getChannel());
        distribution.setStatus(DistributionStatus.PENDING);

        VoucherDistribution saved = distributionRepository.save(distribution);

        // Simulate sending in background
        processDistribution(saved.getId());

        return DistributionResponse.from(saved);
    }

    @Transactional
    public void processDistribution(Long distributionId) {
        VoucherDistribution dist = distributionRepository.findById(distributionId)
                .orElse(null);
        if (dist == null) return;

        try {
            // Mock send - in production would call email/SMS service
            log.info("Sending voucher {} via {} to customer {}",
                    dist.getVoucher().getCode(), dist.getChannel(), dist.getCustomer().getFullName());
            dist.setStatus(DistributionStatus.SENT);
            dist.setSentAt(OffsetDateTime.now());
        } catch (Exception e) {
            dist.setStatus(DistributionStatus.FAILED);
            dist.setErrorMessage(e.getMessage());
        }
        distributionRepository.save(dist);
    }

    @Transactional(readOnly = true)
    public List<DistributionResponse> getByVoucher(Long voucherId) {
        return distributionRepository.findByVoucherId(voucherId)
                .stream().map(DistributionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DistributionResponse> getByStatus(DistributionStatus status) {
        return distributionRepository.findByStatus(status)
                .stream().map(DistributionResponse::from).toList();
    }
}
