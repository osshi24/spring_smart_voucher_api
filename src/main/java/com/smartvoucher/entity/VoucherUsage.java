package com.smartvoucher.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "voucher_usages",
        uniqueConstraints = @UniqueConstraint(
                name = "idx_usages_unique_order",
                columnNames = {"voucher_id", "external_order_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "external_order_id", nullable = false, length = 100)
    private String externalOrderId;

    @Column(name = "external_branch_id", length = 100)
    private String externalBranchId;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "order_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal orderTotal;

    @CreationTimestamp
    @Column(name = "used_at", nullable = false, updatable = false)
    private OffsetDateTime usedAt;
}
