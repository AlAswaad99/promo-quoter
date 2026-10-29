package com.promoquoter.pricing.engine;

import java.util.List;

import com.promoquoter.pricing.rules.AmountOffCartRuleProvider;
import com.promoquoter.pricing.rules.AmountOffRuleProvider;
import com.promoquoter.pricing.rules.FreeGiftRuleProvider;
import com.promoquoter.pricing.rules.FreeUnitsRuleProvider;
import com.promoquoter.pricing.rules.PercentOffRuleProvider;
import com.promoquoter.pricing.rules.RejectRuleProvider;
import com.promoquoter.pricing.rules.ShippingWaiverRuleProvider;
import com.promoquoter.pricing.rules.SurchargeRuleProvider;

public final class TestProviders {

    private TestProviders() {
    }

    public static List<ActionRuleProvider> all() {
        return List.of(new PercentOffRuleProvider(), new FreeUnitsRuleProvider(), new AmountOffRuleProvider(),
                new AmountOffCartRuleProvider(), new FreeGiftRuleProvider(), new ShippingWaiverRuleProvider(),
                new SurchargeRuleProvider(), new RejectRuleProvider());
    }

    public static PromotionRuleFactory factory() {
        return new PromotionRuleFactory(all());
    }
}
