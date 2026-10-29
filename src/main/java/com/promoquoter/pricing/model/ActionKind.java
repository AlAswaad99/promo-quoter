package com.promoquoter.pricing.model;

public enum ActionKind {
    PERCENT_OFF(RuleKind.PRICING, 100),
    FREE_UNITS(RuleKind.PRICING, 200),
    AMOUNT_OFF(RuleKind.PRICING, 300),
    AMOUNT_OFF_CART(RuleKind.PRICING, 400),
    FREE_GIFT(RuleKind.PRICING, 500),
    SHIPPING_WAIVER(RuleKind.PRICING, 600),
    SURCHARGE(RuleKind.PRICING, 700),
    REJECT(RuleKind.VALIDATION, 900);

    public static final int MAX_PRICING_STAGE = 899;
    public static final int MIN_VALIDATION_STAGE = 900;
    public static final int MAX_STAGE = 999;

    private final RuleKind ruleKind;
    private final int defaultStage;

    ActionKind(RuleKind ruleKind, int defaultStage) {
        this.ruleKind = ruleKind;
        this.defaultStage = defaultStage;
    }

    public RuleKind ruleKind() {
        return ruleKind;
    }

    public int defaultStage() {
        return defaultStage;
    }

    public boolean targetsLines() {
        return this == PERCENT_OFF || this == FREE_UNITS || this == AMOUNT_OFF;
    }
}
