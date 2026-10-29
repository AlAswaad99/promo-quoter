package com.promoquoter.pricing.engine;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.promoquoter.common.money.Money;
import com.promoquoter.pricing.model.CartLine;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.PriceBreakdown;

public final class PricingEngine {

    private final PromotionRuleFactory ruleFactory;
    private final Clock clock;
    private final BigDecimal flatShippingFee;

    public PricingEngine(PromotionRuleFactory ruleFactory) {
        this(ruleFactory, Clock.systemUTC(), Money.ZERO);
    }

    public PricingEngine(PromotionRuleFactory ruleFactory, Clock clock, BigDecimal flatShippingFee) {
        this.ruleFactory = ruleFactory;
        this.clock = clock;
        this.flatShippingFee = flatShippingFee;
    }

    public PriceBreakdown price(List<CartLine> cartLines, CustomerSegment customerSegment,
            List<RuleDefinition> rules) {
        PricingPipeline pipeline = new PricingPipeline(ruleFactory.create(rules, customerSegment));
        PricingContext context = PricingContext.start(cartLines, customerSegment, LocalDate.now(clock),
                flatShippingFee);
        pipeline.run(context);
        return context.toBreakdown();
    }
}
