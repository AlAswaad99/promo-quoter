package com.promoquoter.promotion;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.promoquoter.pricing.CustomerSegment;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TieredBulkRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull UUID productId,
        @NotNull @Min(2) @Max(1000) Integer minQty,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal percent,
        Integer priority,
        Boolean active,
        Set<@NotNull CustomerSegment> eligibleSegments) implements CreatePromotionRequest {

    public TieredBulkRequest {
        priority = priority == null ? 0 : priority;
        active = active == null ? Boolean.TRUE : active;
        eligibleSegments = eligibleSegments == null ? Set.of() : eligibleSegments;
    }

    @Override
    @JsonIgnore
    public UUID referencedProductId() {
        return productId;
    }

    @Override
    public Promotion toEntity() {
        return Promotion.tieredBulk(name, active, priority, eligibleSegments, productId, minQty, percent);
    }
}
