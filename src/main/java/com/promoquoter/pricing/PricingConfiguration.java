package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.promoquoter.common.money.Money;

@Configuration
class PricingConfiguration {

    @Bean
    Clock pricingClock() {
        return Clock.systemUTC();
    }

    @Bean
    PromotionRuleFactory promotionRuleFactory(List<ActionRuleProvider> providers) {
        return new PromotionRuleFactory(providers);
    }

    @Bean
    PricingEngine pricingEngine(PromotionRuleFactory ruleFactory, Clock pricingClock,
            @Value("${promoquoter.shipping.flat-fee:0.00}") BigDecimal flatShippingFee) {
        return new PricingEngine(ruleFactory, pricingClock, Money.round(flatShippingFee));
    }
}
