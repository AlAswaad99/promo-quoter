package com.promoquoter.pricing;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PricingConfiguration {

    @Bean
    PromotionRuleFactory promotionRuleFactory(List<RuleProvider> providers) {
        return new PromotionRuleFactory(providers);
    }

    @Bean
    PricingEngine pricingEngine(PromotionRuleFactory ruleFactory) {
        return new PricingEngine(ruleFactory);
    }
}
