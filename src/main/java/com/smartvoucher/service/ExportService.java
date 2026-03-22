package com.smartvoucher.service;

import com.opencsv.CSVWriter;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.VoucherUsage;
import com.smartvoucher.entity.enums.UserRole;
import com.smartvoucher.exception.ResourceNotFoundException;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final VoucherRepository voucherRepository;
    private final CustomerRepository customerRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public byte[] exportVouchers(Specification<Voucher> spec) {
        User user = getCurrentUser();
        Specification<Voucher> filtered = applyVoucherOwner(spec, user);
        List<Voucher> vouchers = voucherRepository.findAll(filtered);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             CSVWriter writer = new CSVWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {

            writer.writeNext(new String[]{"id", "code", "description", "status", "discountType",
                    "discountValue", "minOrderValue", "maxUsageTotal", "currentUsageCount",
                    "validFrom", "validUntil", "createdAt"});

            for (Voucher v : vouchers) {
                writer.writeNext(new String[]{
                        str(v.getId()), v.getCode(), v.getDescription(),
                        str(v.getStatus()), str(v.getDiscountType()),
                        str(v.getDiscountValue()), str(v.getMinOrderValue()),
                        str(v.getMaxUsageTotal()), str(v.getCurrentUsageCount()),
                        str(v.getValidFrom()), str(v.getValidUntil()), str(v.getCreatedAt())
                });
            }
            writer.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Export failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportCustomers(Specification<Customer> spec) {
        User user = getCurrentUser();
        Specification<Customer> filtered = applyCustomerOwner(spec, user);
        List<Customer> customers = customerRepository.findAll(filtered);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             CSVWriter writer = new CSVWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {

            writer.writeNext(new String[]{"id", "externalId", "fullName", "email", "phone", "isActive", "createdAt"});

            for (Customer c : customers) {
                writer.writeNext(new String[]{
                        str(c.getId()), c.getExternalId(), c.getFullName(),
                        c.getEmail(), c.getPhone(), str(c.getIsActive()), str(c.getCreatedAt())
                });
            }
            writer.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Export failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportAllUsages(Specification<VoucherUsage> spec) {
        List<VoucherUsage> usages = voucherUsageRepository.findAll(spec);
        return writeUsagesCsv(usages);
    }

    @Transactional(readOnly = true)
    public byte[] exportVoucherUsages(Long voucherId) {
        return writeUsagesCsv(voucherUsageRepository.findByVoucherId(voucherId));
    }

    private byte[] writeUsagesCsv(List<VoucherUsage> usages) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             CSVWriter writer = new CSVWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {

            writer.writeNext(new String[]{"id", "voucherCode", "customerName", "customerEmail",
                    "externalOrderId", "externalBranchId", "discountAmount", "orderTotal", "status", "usedAt"});

            for (VoucherUsage u : usages) {
                writer.writeNext(new String[]{
                        str(u.getId()),
                        u.getVoucher() != null ? u.getVoucher().getCode() : "",
                        u.getCustomer() != null ? u.getCustomer().getFullName() : "",
                        u.getCustomer() != null ? u.getCustomer().getEmail() : "",
                        u.getExternalOrderId(),
                        str(u.getExternalBranchId()),
                        str(u.getDiscountAmount()), str(u.getOrderTotal()),
                        "COMPLETED", str(u.getUsedAt())
                });
            }
            writer.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Export failed", e);
        }
    }

    private Specification<Voucher> applyVoucherOwner(Specification<Voucher> spec, User user) {
        if (user.getRole() == UserRole.STAFF || user.getRole() == UserRole.USER) {
            Specification<Voucher> ownerSpec = (root, query, cb) -> cb.equal(root.get("createdBy"), user);
            return spec == null ? ownerSpec : spec.and(ownerSpec);
        }
        return spec != null ? spec : Specification.where(null);
    }

    private Specification<Customer> applyCustomerOwner(Specification<Customer> spec, User user) {
        if (user.getRole() == UserRole.STAFF || user.getRole() == UserRole.USER) {
            Specification<Customer> ownerSpec = (root, query, cb) -> cb.equal(root.get("createdBy"), user);
            return spec == null ? ownerSpec : spec.and(ownerSpec);
        }
        return spec != null ? spec : Specification.where(null);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
