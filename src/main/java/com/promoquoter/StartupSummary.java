package com.promoquoter;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.promoquoter.common.observability.LogEvent;
import com.promoquoter.promotion.domain.PromotionService;

@Component
class StartupSummary {

    private static final Logger LOG = LoggerFactory.getLogger(StartupSummary.class);

    private final Environment environment;
    private final PromotionService promotionService;
    private final String shippingFee;

    StartupSummary(Environment environment, PromotionService promotionService,
            @Value("${promoquoter.shipping.flat-fee:0.00}") String shippingFee) {
        this.environment = environment;
        this.promotionService = promotionService;
        this.shippingFee = shippingFee;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logSummary() {
        String version = StartupSummary.class.getPackage().getImplementationVersion();
        String[] profiles = environment.getActiveProfiles();
        LogEvent.APP_STARTED.at(LOG)
                .with("version", version == null ? "dev" : version)
                .with("profiles", profiles.length == 0 ? "default" : String.join(",", Arrays.asList(profiles)))
                .with("shippingFee", shippingFee)
                .with("activePromotions", promotionService.countActive())
                .message("PromoQuoter started");
    }
}
