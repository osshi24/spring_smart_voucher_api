package com.smartvoucher.integration;

import com.smartvoucher.dto.request.VoucherRedeemRequest;
import com.smartvoucher.entity.Customer;
import com.smartvoucher.entity.User;
import com.smartvoucher.entity.Voucher;
import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import com.smartvoucher.repository.CustomerRepository;
import com.smartvoucher.repository.UserRepository;
import com.smartvoucher.repository.VoucherRepository;
import com.smartvoucher.service.VoucherRedemptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class ConcurrentRedeemTest {

    @Autowired
    private VoucherRedemptionService redemptionService;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    private Voucher voucher;
    private List<Customer> customers;
    private String testRunId;

    @BeforeEach
    void setUp() {
        testRunId = UUID.randomUUID().toString().substring(0, 8);

        // Get an existing admin user for createdBy field
        User adminUser = userRepository.findByUsername("admin01")
                .orElseThrow(() -> new IllegalStateException("admin01 user not found - check Flyway migrations"));

        // Create 100 unique customers for this test run
        customers = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Customer customer = new Customer();
            customer.setExternalId("CC_" + testRunId + "_" + i);
            customer.setFullName("Concurrent Customer " + i);
            customer.setEmail("cc_" + testRunId + "_" + i + "@test.com");
            customer.setIsActive(true);
            customers.add(customerRepository.save(customer));
        }

        // Create voucher with max 50 usages
        voucher = new Voucher();
        voucher.setCode("CT_" + testRunId.toUpperCase());
        voucher.setDiscountType(DiscountType.PERCENTAGE);
        voucher.setDiscountValue(BigDecimal.TEN);
        voucher.setMinOrderValue(BigDecimal.ZERO);
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setIsPublic(true);
        voucher.setMaxUsageTotal(50);
        voucher.setCurrentUsageCount(0);
        voucher.setApplicableProducts(new ArrayList<>());
        voucher.setApplicableCategories(new ArrayList<>());
        voucher.setApplicableBranches(new ArrayList<>());
        voucher.setValidFrom(OffsetDateTime.now().minusDays(1));
        voucher.setValidUntil(OffsetDateTime.now().plusDays(30));
        voucher.setCreatedBy(adminUser);
        voucher = voucherRepository.save(voucher);
    }

    @Test
    void concurrentRedeem_exactlyMaxUsageSucceed() throws InterruptedException {
        int threadCount = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        String voucherCode = voucher.getCode();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            final Long customerId = customers.get(i).getId();
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    VoucherRedeemRequest req = new VoucherRedeemRequest();
                    req.setVoucherCode(voucherCode);
                    req.setCustomerId(customerId);
                    req.setExternalOrderId("ORD_" + testRunId.toUpperCase() + "_" + idx);
                    req.setOrderTotal(BigDecimal.valueOf(100000));
                    redemptionService.redeem(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads simultaneously
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failureCount.get()).isEqualTo(50);

        Voucher updated = voucherRepository.findById(voucher.getId()).orElseThrow();
        assertThat(updated.getCurrentUsageCount()).isEqualTo(50);
        assertThat(updated.getStatus()).isEqualTo(VoucherStatus.FULLY_USED);
    }
}
