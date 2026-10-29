package com.promoquoter.cart.dto;

import com.promoquoter.promotion.dto.CreatePromotionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RulePreviewRequest(
        @NotNull @Valid CreatePromotionRequest rule,
        @NotNull @Valid CartRequest cart) {
}
