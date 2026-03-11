package com.smartvoucher.entity;

import com.smartvoucher.entity.enums.DiscountType;
import com.smartvoucher.entity.enums.VoucherStatus;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 50)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount", precision = 15, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal minOrderValue = BigDecimal.ZERO;

    @Type(JsonType.class)
    @Column(name = "applicable_products", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> applicableProducts = new ArrayList<>();

    @Type(JsonType.class)
    @Column(name = "applicable_categories", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> applicableCategories = new ArrayList<>();

    @Type(JsonType.class)
    @Column(name = "applicable_branches", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> applicableBranches = new ArrayList<>();

    @Column(name = "max_usage_total")
    private Integer maxUsageTotal;

    @Column(name = "max_usage_per_customer")
    private Integer maxUsagePerCustomer;

    @Column(name = "current_usage_count", nullable = false)
    private Integer currentUsageCount = 0;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private OffsetDateTime validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VoucherStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
