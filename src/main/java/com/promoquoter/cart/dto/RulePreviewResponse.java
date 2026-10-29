package com.promoquoter.cart.dto;

import java.util.List;

import com.promoquoter.promotion.dto.PromotionResponse;

public record RulePreviewResponse(PromotionResponse rule, QuoteResponse quote, List<RuleViolationEntry> violations) {
}
