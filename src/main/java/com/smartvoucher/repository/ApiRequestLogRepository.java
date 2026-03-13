package com.smartvoucher.repository;

import com.smartvoucher.entity.ApiRequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long>,
        JpaSpecificationExecutor<ApiRequestLog> {
    Page<ApiRequestLog> findByApiKeyId(Long apiKeyId, Pageable pageable);
    Page<ApiRequestLog> findByEndpoint(String endpoint, Pageable pageable);
}
