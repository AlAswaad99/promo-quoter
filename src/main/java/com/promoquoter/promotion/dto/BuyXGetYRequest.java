package com.promoquoter.promotion.dto;

import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.promoquoter.pricing.CustomerSegment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BuyXGetYRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull UUID productId,
        @NotNull @Min(1) @Max(1000) Integer buyQty,
        @NotNull @Min(1) @Max(1000) Integer freeQty,
        Integer priority,
        Boolean active,
        Set<@NotNull CustomerSegment> eligibleSegments) implements CreatePromotionRequest {

    public BuyXGetYRequest {
        priority = priority == null ? 0 : priority;
        active = active == null ? Boolean.TRUE : active;
        eligibleSegments = eligibleSegments == null ? Set.of() : eligibleSegments;
    }

    @Override
    @JsonIgnore
    public UUID referencedProductId() {
        return productId;
    }
}
