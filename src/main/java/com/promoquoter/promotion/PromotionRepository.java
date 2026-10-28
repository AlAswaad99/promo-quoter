package com.promoquoter.promotion;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.promoquoter.pricing.PromotionType;

interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    @Query("""
            select p from Promotion p
            where (:type is null or p.type = :type)
              and (:active is null or p.active = :active)
            """)
    Page<Promotion> search(@Param("type") PromotionType type, @Param("active") Boolean active, Pageable pageable);
}
