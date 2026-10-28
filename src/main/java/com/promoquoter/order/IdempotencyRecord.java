package com.promoquoter.order;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idempotency_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    IdempotencyRecord(String key, String requestHash, UUID orderId) {
        this.key = key;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
