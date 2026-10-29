package com.promoquoter.pricing.condition;

import java.util.Arrays;
import java.util.Optional;

public enum Operator {
    EQ("eq"),
    NE("ne"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    BETWEEN("between"),
    IN("in"),
    NOT_IN("notIn"),
    STARTS_WITH("startsWith"),
    CONTAINS("contains");

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean takesList() {
        return this == IN || this == NOT_IN;
    }

    public boolean takesRange() {
        return this == BETWEEN;
    }

    public static Optional<Operator> bySymbol(String symbol) {
        return Arrays.stream(values()).filter(operator -> operator.symbol.equals(symbol)).findFirst();
    }
}
