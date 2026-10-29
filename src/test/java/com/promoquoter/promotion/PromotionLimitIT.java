package com.promoquoter.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class PromotionLimitIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PromotionRepository promotionRepository;

    @Autowired
    TestDatabase database;

    @BeforeEach
    void fillToJustUnderTheLimit() {
        database.clean();
        List<Promotion> promotions = new ArrayList<>();
        for (int index = 0; index < PromotionService.MAX_ACTIVE_PROMOTIONS - 1; index++) {
            promotions.add(Promotion.percentOffCategory("P" + index, true, 0, Set.of(), "k", new java.math.BigDecimal("1")));
        }
        promotionRepository.saveAll(promotions);
    }

    private ResultActions create(String active) throws Exception {
        return mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"type\":\"PERCENT_OFF_CATEGORY\",\"name\":\"X\",\"category\":\"k\",\"percent\":5,"
                        + "\"active\":" + active + "}]"));
    }

    @Test
    void theLastActivePromotionIsAccepted() throws Exception {
        create("true").andExpect(status().isCreated());

        assertThat(promotionRepository.countByActiveTrue()).isEqualTo(PromotionService.MAX_ACTIVE_PROMOTIONS);
    }

    @Test
    void anotherActivePromotionIsRejected() throws Exception {
        create("true").andExpect(status().isCreated());

        create("true").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("promotions"));
        assertThat(promotionRepository.count()).isEqualTo(PromotionService.MAX_ACTIVE_PROMOTIONS);
    }

    @Test
    void inactivePromotionsDoNotCountTowardTheLimit() throws Exception {
        create("true").andExpect(status().isCreated());

        create("false").andExpect(status().isCreated());
    }

    @Test
    void aBatchThatWouldCrossTheLimitIsRejectedAsAWhole() throws Exception {
        mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"type\":\"PERCENT_OFF_CATEGORY\",\"name\":\"A\",\"category\":\"k\",\"percent\":5},"
                                + "{\"type\":\"PERCENT_OFF_CATEGORY\",\"name\":\"B\",\"category\":\"k\",\"percent\":5}]"))
                .andExpect(status().isBadRequest());

        assertThat(promotionRepository.count()).isEqualTo(PromotionService.MAX_ACTIVE_PROMOTIONS - 1);
    }

    @Test
    void reactivatingAPromotionBeyondTheLimitIsRejected() throws Exception {
        Promotion inactive = promotionRepository.save(
                Promotion.percentOffCategory("Off", false, 0, Set.of(), "k", new java.math.BigDecimal("1")));
        create("true").andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/promotions/" + inactive.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("promotions"));
    }
}
