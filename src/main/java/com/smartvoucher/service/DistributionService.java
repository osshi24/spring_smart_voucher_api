package com.smartvoucher.service;

import com.smartvoucher.dto.request.DistributionCreateRequest;
import com.smartvoucher.dto.response.DistributionResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherDistribution;
import com.smartvoucher.entity.enums.DistributionChannel;
import com.smartvoucher.entity.enums.DistributionStatus;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.exception.ConflictException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.DistributionRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final com.smartvoucher.repository.VoucherCodeRepository voucherCodeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final QrTokenService qrTokenService;

    @Transactional
    public DistributionResponse create(DistributionCreateRequest req) {
        Voucher voucher = voucherRepository.findById(req.getVoucherId())
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + req.getVoucherId()));

        // STAFF/USER can only distribute vouchers they own
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser) && !voucher.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Voucher not found: " + req.getVoucherId());
        }

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
            Voucher voucher = dist.getVoucher();
            Customer customer = dist.getCustomer();
            log.info("Sending voucher {} via {} to customer {}",
                    voucher.getCode(), dist.getChannel(), customer.getFullName());

            // Assign unique code if applicable
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

    @Transactional
    public DistributionResponse retry(Long id) {
        VoucherDistribution dist = findById(id);
        if (dist.getStatus() != DistributionStatus.FAILED) {
            throw new ConflictException("Only FAILED distributions can be retried. Current status: " + dist.getStatus());
        }
        dist.setStatus(DistributionStatus.PENDING);
        dist.setErrorMessage(null);
        dist.setFailureReason(null);
        distributionRepository.save(dist);
        processDistribution(dist.getId());
        return DistributionResponse.from(distributionRepository.findById(id).orElseThrow());
    }

    @Transactional
    public DistributionResponse resend(Long distributionId) {
        VoucherDistribution dist = findById(distributionId);

        if (dist.getChannel() != DistributionChannel.EMAIL) {
            throw new IllegalArgumentException("Resend is only supported for EMAIL channel");
        }

        Voucher voucher = dist.getVoucher();
        com.smartvoucher.entity.Customer customer = dist.getCustomer();

        if (customer.getEmail() == null) {
            throw new IllegalArgumentException("Customer does not have an email address");
        }

        try {
            Long merchantId = voucher.getCreatedBy() != null ? voucher.getCreatedBy().getId() : null;
            String token = qrTokenService.generateQrToken(voucher.getCode(), customer.getId(), merchantId);
            String qrUrl = qrTokenService.buildQrUrl(token);
            byte[] qrImage = qrTokenService.generateQrImage(qrUrl);
            emailService.sendVoucherEmail(customer.getEmail(), voucher, customer.getFullName(), qrImage);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resend email: " + e.getMessage());
        }

        // Only update status to SENT if it was FAILED (SENT distributions keep their status)
        if (dist.getStatus() == DistributionStatus.FAILED) {
            dist.setStatus(DistributionStatus.SENT);
            dist.setSentAt(java.time.OffsetDateTime.now());
            dist.setErrorMessage(null);
            dist.setFailureReason(null);
            distributionRepository.save(dist);
        }

        return DistributionResponse.from(dist);
    }

    @Transactional(readOnly = true)
    public Page<DistributionResponse> getAll(Specification<VoucherDistribution> spec, Pageable pageable) {
        return distributionRepository.findAll(withOwnerFilter(spec), pageable)
                .map(DistributionResponse::from);
    }

    @Transactional(readOnly = true)
    public DistributionResponse getById(Long id) {
        return DistributionResponse.from(findById(id));
    }

    @Transactional
    public void cancel(Long id) {
        VoucherDistribution dist = findById(id);
        if (voucherUsageRepository.existsByVoucherIdAndCustomerId(
                dist.getVoucher().getId(), dist.getCustomer().getId())) {
            throw new ConflictException("Cannot cancel: the voucher has already been redeemed by the customer.");
        }
        dist.setStatus(DistributionStatus.CANCELLED);
        distributionRepository.save(dist);
    }

    private VoucherDistribution findById(Long id) {
        VoucherDistribution dist = distributionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Distribution not found: " + id));
        checkOwnership(dist);
        return dist;
    }

    private void checkOwnership(VoucherDistribution dist) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)
                && !dist.getVoucher().getCreatedBy().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Distribution not found: " + dist.getId());
        }
    }

    private Specification<VoucherDistribution> withOwnerFilter(Specification<VoucherDistribution> spec) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)) {
            User owner = currentUser;
            Specification<VoucherDistribution> ownerSpec =
                    (root, query, cb) -> cb.equal(root.get("voucher").get("createdBy"), owner);
            return spec == null ? ownerSpec : spec.and(ownerSpec);
        }
        return spec != null ? spec : Specification.where(null);
    }

    private boolean isRestricted(User user) {
        return user.getRole() == UserRole.STAFF || user.getRole() == UserRole.USER;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
