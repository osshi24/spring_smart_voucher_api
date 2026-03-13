package com.smartvoucher.repository;

import com.smartvoucher.entity.Campaign;
import com.smartvoucher.entity.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);
}
