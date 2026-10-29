package com.promoquoter.cart;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.promotion.PromotionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RulePreviewService {

    private final PromotionService promotionService;
    private final CartNormalizer cartNormalizer;
    private final QuoteService quoteService;

    @Transactional(readOnly = true)
    public RulePreviewResponse preview(RulePreviewRequest request) {
        PromotionService.PreviewRule rule = promotionService.previewRule(request.rule());
        NormalizedCart cart = cartNormalizer.normalize(request.cart());
        PriceBreakdown breakdown = quoteService.price(cart, List.of(rule.definition()));
        return new RulePreviewResponse(rule.response(), QuoteResponse.from(breakdown),
                breakdown.violations().stream().map(RuleViolationEntry::from).toList());
    }
}
