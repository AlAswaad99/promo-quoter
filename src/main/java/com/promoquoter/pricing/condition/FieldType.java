package com.promoquoter.pricing.condition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.promoquoter.pricing.model.CustomerSegment;

public enum FieldType {
    NUMBER(BigDecimal.class, EnumSet.of(Operator.EQ, Operator.NE, Operator.GT, Operator.GTE, Operator.LT, Operator.LTE,
            Operator.BETWEEN, Operator.IN, Operator.NOT_IN)),
    TEXT(String.class, EnumSet.of(Operator.EQ, Operator.NE, Operator.IN, Operator.NOT_IN, Operator.STARTS_WITH,
            Operator.CONTAINS)),
    ID(UUID.class, EnumSet.of(Operator.EQ, Operator.NE, Operator.IN, Operator.NOT_IN)),
    SEGMENT(CustomerSegment.class, EnumSet.of(Operator.EQ, Operator.NE, Operator.IN, Operator.NOT_IN)),
    DATE(LocalDate.class, EnumSet.of(Operator.EQ, Operator.NE, Operator.GT, Operator.GTE, Operator.LT, Operator.LTE,
            Operator.BETWEEN));

    private final Class<?> valueType;
    private final Set<Operator> operators;

    FieldType(Class<?> valueType, Set<Operator> operators) {
        this.valueType = valueType;
        this.operators = operators;
    }

    public Class<?> valueType() {
        return valueType;
    }

    public boolean supports(Operator operator) {
        return operators.contains(operator);
    }

    public Set<Operator> operators() {
        return EnumSet.copyOf(operators);
    }
}
