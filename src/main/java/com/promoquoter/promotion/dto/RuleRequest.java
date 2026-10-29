package com.promoquoter.promotion.dto;

import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.RuleKind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RuleRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull RuleKind kind,
        @Min(-1000000) @Max(1000000) Integer priority,
        @Min(0) @Max(999) Integer stage,
        Boolean stopAfter,
        Boolean active,
        Set<@NotNull CustomerSegment> eligibleSegments,
        JsonNode when,
        JsonNode target,
        JsonNode action) implements CreatePromotionRequest {

    public RuleRequest {
        priority = priority == null ? 0 : priority;
        stopAfter = stopAfter == null ? Boolean.FALSE : stopAfter;
        active = active == null ? Boolean.TRUE : active;
        eligibleSegments = eligibleSegments == null ? Set.of() : eligibleSegments;
    }

    @Override
    @JsonIgnore
    public UUID referencedProductId() {
        return null;
    }
}
