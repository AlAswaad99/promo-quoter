package com.promoquoter.cart;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.promotion.domain.PromotionService;

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
        QuotedCart quoted = quoteService.price(cart, List.of(rule.definition()), false);
        return new RulePreviewResponse(rule.response(), QuoteResponse.from(quoted),
                quoted.breakdown().violations().stream().map(RuleViolationEntry::from).toList());
    }
}
