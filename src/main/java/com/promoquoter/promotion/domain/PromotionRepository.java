package com.promoquoter.promotion.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.RuleKind;

interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    List<Promotion> findByActiveTrue();

    long countByActiveTrue();

    @Query("""
            select p from Promotion p
            where (:type is null or p.type = :type)
              and (:active is null or p.active = :active)
              and (:kind is null or p.kind = :kind)
            """)
    Page<Promotion> search(@Param("type") PromotionType type, @Param("active") Boolean active,
            @Param("kind") RuleKind kind, Pageable pageable);
}
