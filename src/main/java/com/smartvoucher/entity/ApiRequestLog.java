package com.smartvoucher.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;

@Entity
@Table(name = "api_request_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id")
    private ApiKey apiKey;

    @Column(nullable = false, length = 200)
    private String endpoint;

    @Column(nullable = false, length = 10)
    private String method;

    @Type(JsonType.class)
    @Column(name = "request_body", columnDefinition = "jsonb")
    private Object requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Type(JsonType.class)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private Object responseBody;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public void setApiKeyId(Long id) {
        if (id != null) {
            ApiKey key = new ApiKey();
            key.setId(id);
            this.apiKey = key;
        }
    }
}
