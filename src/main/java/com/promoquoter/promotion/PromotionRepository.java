package com.promoquoter.promotion;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PromotionRepository extends JpaRepository<Promotion, UUID> {
}
