package com.promoquoter.promotion;

import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.promoquoter.pricing.CustomerSegment;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PercentOffCategoryRequest.class, name = "PERCENT_OFF_CATEGORY"),
        @JsonSubTypes.Type(value = BuyXGetYRequest.class, name = "BUY_X_GET_Y"),
        @JsonSubTypes.Type(value = TieredBulkRequest.class, name = "TIERED_BULK"),
        @JsonSubTypes.Type(value = RuleRequest.class, name = "RULE")
})
public sealed interface CreatePromotionRequest permits PercentOffCategoryRequest, BuyXGetYRequest, TieredBulkRequest, RuleRequest {

    String name();

    Boolean active();

    Integer priority();

    Set<CustomerSegment> eligibleSegments();

    UUID referencedProductId();

    Promotion toEntity();
}
