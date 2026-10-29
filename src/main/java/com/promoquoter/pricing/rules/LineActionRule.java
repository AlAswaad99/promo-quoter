package com.promoquoter.pricing.rules;

import com.promoquoter.pricing.engine.PricedLine;
import com.promoquoter.pricing.engine.PricingContext;
import com.promoquoter.pricing.engine.RuleDefinition;

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
