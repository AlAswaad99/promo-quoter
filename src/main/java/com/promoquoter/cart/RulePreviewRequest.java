package com.promoquoter.cart;

import com.promoquoter.promotion.CreatePromotionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RulePreviewRequest(
        @NotNull @Valid CreatePromotionRequest rule,
        @NotNull @Valid CartRequest cart) {
}
