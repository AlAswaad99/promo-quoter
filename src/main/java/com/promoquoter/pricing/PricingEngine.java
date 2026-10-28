package com.promoquoter.pricing;

import java.util.List;

public final class PricingEngine {

    private final PromotionRuleFactory ruleFactory;

    public PricingEngine(PromotionRuleFactory ruleFactory) {
        this.ruleFactory = ruleFactory;
    }

    public PriceBreakdown price(List<CartLine> cartLines, CustomerSegment customerSegment,
            List<PromotionDefinition> promotions) {
        PricingPipeline pipeline = new PricingPipeline(ruleFactory.create(promotions, customerSegment));
        PricingContext context = PricingContext.start(cartLines, customerSegment);
        pipeline.run(context);
        return context.toBreakdown();
    }
}
