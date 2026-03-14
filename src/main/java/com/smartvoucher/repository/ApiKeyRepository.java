package com.smartvoucher.repository;

import com.smartvoucher.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long>, JpaSpecificationExecutor<ApiKey> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findByIsActiveTrue();
    boolean existsByKeyHash(String keyHash);
}
