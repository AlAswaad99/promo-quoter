package com.promoquoter.order;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {
}
