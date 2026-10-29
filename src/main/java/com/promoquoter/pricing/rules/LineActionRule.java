package com.promoquoter.pricing.rules;

import com.promoquoter.pricing.PricedLine;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.RuleDefinition;

public abstract class LineActionRule extends GenericRule {

    protected LineActionRule(RuleDefinition definition) {
        super(definition);
    }

    @Override
    protected final void execute(PricingContext context) {
        targetLines(context).forEach(line -> applyToLine(line));
    }

    protected abstract void applyToLine(PricedLine line);
}
