package com.smartvoucher.service;

import com.smartvoucher.dto.request.CustomerCreateRequest;
import com.smartvoucher.dto.response.BulkOperationResponse;
import com.smartvoucher.dto.response.CustomerResponse;
import com.smartvoucher.dto.response.CustomerUsageResponse;
import com.smartvoucher.dto.response.CustomerVoucherResponse;
import com.smartvoucher.dto.response.PosAvailableVoucherResponse;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.service.CustomerResolutionService;
import com.smartvoucher.exception.ConflictException;
import com.smartvoucher.exception.DuplicateResourceException;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherCustomerRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.CsvBindByName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final VoucherCustomerRepository voucherCustomerRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final UserRepository userRepository;
    private final CustomerResolutionService customerResolutionService;

    @Transactional
    public CustomerResponse create(CustomerCreateRequest req) {
        if (req.getExternalId() != null && customerRepository.existsByExternalId(req.getExternalId())) {
            throw new DuplicateResourceException("Customer with external ID already exists: " + req.getExternalId());
        }
        if (req.getEmail() != null && customerRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateResourceException("Customer with email already exists: " + req.getEmail());
        }

        User currentUser = getCurrentUser();
        Customer customer = new Customer();
        customer.setFullName(req.getFullName());
        customer.setExternalId(req.getExternalId());
        customer.setEmail(req.getEmail());
        customer.setPhone(req.getPhone());
        customer.setIsActive(true);
        customer.setCreatedBy(currentUser);

        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAll(Specification<Customer> spec, Pageable pageable) {
        return customerRepository.findAll(withOwnerFilter(spec), pageable).map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return CustomerResponse.from(findById(id));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByExternalId(String externalId) {
        Customer customer = customerRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + externalId));
        checkOwnership(customer);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerCreateRequest req) {
        Customer customer = findById(id);
        if (req.getFullName() != null) customer.setFullName(req.getFullName());
        if (req.getEmail() != null) customer.setEmail(req.getEmail());
        if (req.getPhone() != null) customer.setPhone(req.getPhone());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerVoucherResponse> getCustomerVouchers(Long customerId, String voucherCode,
                                                              String discountType, Pageable pageable) {
        findById(customerId); // ownership check
        Specification<com.smartvoucher.entity.VoucherCustomer> spec =
                (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
        if (voucherCode != null && !voucherCode.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("voucher").get("code")), "%" + voucherCode.toLowerCase() + "%"));
        }
        if (discountType != null && !discountType.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("voucher").get("discountType").as(String.class), discountType));
        }
        final Long cid = customerId;
        return voucherCustomerRepository.findAll(spec, pageable)
                .map(vc -> {
                    boolean used = voucherUsageRepository.existsByVoucherIdAndCustomerId(
                            vc.getVoucher().getId(), cid);
                    return CustomerVoucherResponse.from(vc, used);
                });
    }

    @Transactional(readOnly = true)
    public Page<CustomerUsageResponse> getCustomerUsages(Long customerId, Long voucherId,
                                                          java.time.OffsetDateTime usedAtFrom,
                                                          java.time.OffsetDateTime usedAtTo,
                                                          Pageable pageable) {
        findById(customerId); // ownership check
        Specification<com.smartvoucher.entity.VoucherUsage> spec =
                (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
        if (voucherId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("voucher").get("id"), voucherId));
        }
        if (usedAtFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("usedAt"), usedAtFrom));
        }
        if (usedAtTo != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("usedAt"), usedAtTo));
        }
        return voucherUsageRepository.findAll(spec, pageable).map(CustomerUsageResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PosAvailableVoucherResponse> getAvailableVouchersForCustomer(String ref, BigDecimal orderTotal) {
        // Resolve customer without auto-create (throws 404 if not found)
        com.smartvoucher.entity.Customer customer = customerResolutionService.resolve(null, ref, null, false);

        OffsetDateTime now = OffsetDateTime.now();
        List<com.smartvoucher.entity.VoucherCustomer> assignments =
                voucherCustomerRepository.findActiveVouchersForCustomer(customer.getId(), now);

        return assignments.stream()
                .filter(vc -> {
                    // Filter vouchers where customer has NOT exceeded maxUsagePerCustomer
                    if (vc.getVoucher().getMaxUsagePerCustomer() != null) {
                        long usageCount = voucherUsageRepository.countByVoucherIdAndCustomerId(
                                vc.getVoucher().getId(), customer.getId());
                        if (usageCount >= vc.getVoucher().getMaxUsagePerCustomer()) {
                            return false;
                        }
                    }
                    // Filter by minOrderValue if orderTotal provided
                    if (orderTotal != null && vc.getVoucher().getMinOrderValue() != null) {
                        if (orderTotal.compareTo(vc.getVoucher().getMinOrderValue()) < 0) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(vc -> PosAvailableVoucherResponse.from(vc.getVoucher()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public CustomerResponse deactivate(Long id) {
        Customer customer = findById(id);
        if (!customer.getIsActive()) {
            throw new ConflictException("Customer is already inactive");
        }
        customer.setIsActive(false);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse activate(Long id) {
        Customer customer = findById(id);
        if (customer.getIsActive()) {
            throw new ConflictException("Customer is already active");
        }
        customer.setIsActive(true);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public void delete(Long id) {
        customerRepository.delete(findById(id));
    }

    @Transactional
    public BulkOperationResponse importFromCsv(MultipartFile file) {
        List<BulkOperationResponse.BulkError> errors = new ArrayList<>();
        int processed = 0, skipped = 0;
        User currentUser = getCurrentUser();

        List<CustomerCsvRow> rows;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CsvToBean<CustomerCsvRow> csvToBean = new CsvToBeanBuilder<CustomerCsvRow>(reader)
                    .withType(CustomerCsvRow.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreEmptyLine(true)
                    .build();
            rows = csvToBean.parse();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV: " + e.getMessage());
        }

        for (CustomerCsvRow row : rows) {
            try {
                boolean exists = (row.getExternalId() != null && !row.getExternalId().isBlank()
                        && customerRepository.existsByExternalId(row.getExternalId()))
                        || (row.getEmail() != null && !row.getEmail().isBlank()
                        && customerRepository.existsByEmail(row.getEmail()));
                if (exists) {
                    skipped++;
                    continue;
                }
                Customer customer = new Customer();
                customer.setFullName(row.getFullName());
                customer.setExternalId(row.getExternalId());
                customer.setEmail(row.getEmail() != null ? row.getEmail().toLowerCase() : null);
                customer.setPhone(row.getPhone());
                customer.setIsActive(true);
                customer.setCreatedBy(currentUser);
                customerRepository.save(customer);
                processed++;
            } catch (Exception e) {
                errors.add(BulkOperationResponse.BulkError.builder()
                        .ref(row.getExternalId() != null ? row.getExternalId() : row.getEmail())
                        .reason(e.getMessage())
                        .build());
            }
        }
        return BulkOperationResponse.builder()
                .total(rows.size())
                .processed(processed)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerCsvRow {
        @CsvBindByName(column = "fullName", required = true)
        private String fullName;

        @CsvBindByName(column = "externalId")
        private String externalId;

        @CsvBindByName(column = "email")
        private String email;

        @CsvBindByName(column = "phone")
        private String phone;
    }

    private Customer findById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
        checkOwnership(customer);
        return customer;
    }

    private void checkOwnership(Customer customer) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)
                && (customer.getCreatedBy() == null
                    || !customer.getCreatedBy().getId().equals(currentUser.getId()))) {
            throw new ResourceNotFoundException("Customer not found: " + customer.getId());
        }
    }

    private Specification<Customer> withOwnerFilter(Specification<Customer> spec) {
        User currentUser = getCurrentUser();
        if (isRestricted(currentUser)) {
            User owner = currentUser;
            Specification<Customer> ownerSpec = (root, query, cb) -> cb.equal(root.get("createdBy"), owner);
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
