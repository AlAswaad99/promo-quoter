package com.promoquoter.pricing.condition;

@FunctionalInterface
public interface Facts {

    Object valueOf(ConditionField field);
}
