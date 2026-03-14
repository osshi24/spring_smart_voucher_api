package com.smartvoucher.service;

import com.smartvoucher.dto.request.DistributionCreateRequest;
import com.smartvoucher.dto.response.DistributionResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherDistribution;
import com.smartvoucher.entity.enums.DistributionStatus;
import com.smartvoucher.exception.ConflictException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.DistributionRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributionService {

    private final DistributionRepository distributionRepository;
    private final VoucherRepository voucherRepository;
    private final CustomerRepository customerRepository;
    private final VoucherUsageRepository voucherUsageRepository;

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
        processDistribution(saved.getId());
        return DistributionResponse.from(saved);
    }

    @Transactional
    public void processDistribution(Long distributionId) {
        VoucherDistribution dist = distributionRepository.findById(distributionId).orElse(null);
        if (dist == null) return;
        try {
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
    public Page<DistributionResponse> getAll(Specification<VoucherDistribution> spec, Pageable pageable) {
        return distributionRepository.findAll(spec != null ? spec : Specification.where(null), pageable)
                .map(DistributionResponse::from);
    }

    @Transactional(readOnly = true)
    public DistributionResponse getById(Long id) {
        VoucherDistribution dist = distributionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Distribution not found: " + id));
        return DistributionResponse.from(dist);
    }

    @Transactional
    public void cancel(Long id) {
        VoucherDistribution dist = distributionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Distribution not found: " + id));
        if (voucherUsageRepository.existsByVoucherIdAndCustomerId(
                dist.getVoucher().getId(), dist.getCustomer().getId())) {
            throw new ConflictException("Cannot cancel: the voucher has already been redeemed by the customer.");
        }
        dist.setStatus(DistributionStatus.CANCELLED);
        distributionRepository.save(dist);
    }
}
