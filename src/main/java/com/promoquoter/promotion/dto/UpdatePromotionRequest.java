package com.promoquoter.promotion.dto;

import java.util.Set;

import com.promoquoter.pricing.CustomerSegment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePromotionRequest(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 200) String name,
        Boolean active,
        @Min(-1000000) @Max(1000000) Integer priority,
        Set<@NotNull CustomerSegment> eligibleSegments) {

    public boolean isEmpty() {
        return name == null && active == null && priority == null && eligibleSegments == null;
    }
}
