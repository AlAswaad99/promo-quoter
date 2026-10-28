package com.promoquoter.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@Component
public class ApiFixtures {

    @Autowired
    MockMvc mockMvc;

    public String createProduct(String name, String category, String price, int stock) throws Exception {
        String body = mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"" + name + "\",\"category\":\"" + category + "\",\"price\":\"" + price
                                + "\",\"stock\":" + stock + "}]"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    public String createPromotion(String json) throws Exception {
        String body = mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + json + "]"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    public String percentOff(String name, String category, String percent, String extra) throws Exception {
        return createPromotion("{\"type\":\"PERCENT_OFF_CATEGORY\",\"name\":\"" + name + "\",\"category\":\""
                + category + "\",\"percent\":" + percent + extra + "}");
    }

    public String buyXGetY(String name, String productId, int buy, int free, String extra) throws Exception {
        return createPromotion("{\"type\":\"BUY_X_GET_Y\",\"name\":\"" + name + "\",\"productId\":\"" + productId
                + "\",\"buyQty\":" + buy + ",\"freeQty\":" + free + extra + "}");
    }
}
