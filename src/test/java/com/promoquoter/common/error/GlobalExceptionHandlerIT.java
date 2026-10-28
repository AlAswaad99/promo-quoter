package com.promoquoter.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.promoquoter.support.IntegrationTest;

@IntegrationTest
class GlobalExceptionHandlerIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void bodyValidationFailureListsEveryViolatedField() throws Exception {
        mockMvc.perform(post("/test-support/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"qty\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value("urn:promoquoter:error:validation-failed"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[1].field").value("qty"));
    }

    @Test
    void listValidationFailureReportsElementIndex() throws Exception {
        mockMvc.perform(post("/test-support/list").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"ok\",\"qty\":1},{\"name\":\"\",\"qty\":1}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("[1].name"));
    }

    @Test
    void listSizeConstraintFailureIsReported() throws Exception {
        mockMvc.perform(post("/test-support/list").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    @Test
    void requestParamConstraintFailureIsReported() throws Exception {
        mockMvc.perform(get("/test-support/param").param("n", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("n"));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/test-support/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void wrongFieldTypeNamesTheField() throws Exception {
        mockMvc.perform(post("/test-support/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"qty\":\"many\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("qty"));
    }

    @Test
    void missingBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/test-support/body").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void pathVariableTypeMismatchIsMalformed() throws Exception {
        mockMvc.perform(get("/test-support/items/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void apiExceptionCarriesCodeStatusAndDetails() throws Exception {
        mockMvc.perform(get("/test-support/api-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.title").value("Insufficient stock"))
                .andExpect(jsonPath("$.detail").value("Not enough stock"))
                .andExpect(jsonPath("$.instance").value("/test-support/api-error"))
                .andExpect(jsonPath("$.errors[0].productId").value("p-1"))
                .andExpect(jsonPath("$.errors[0].requested").value(5))
                .andExpect(jsonPath("$.errors[0].available").value(2));
    }

    @Test
    void apiExceptionWithoutDetailsOmitsErrorsArray() throws Exception {
        mockMvc.perform(get("/test-support/plain-api-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void lockFailuresMapToConflict() throws Exception {
        mockMvc.perform(get("/test-support/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        mockMvc.perform(get("/test-support/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void unknownRouteIsProblemDetail() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unsupportedMethodIsProblemDetail() throws Exception {
        mockMvc.perform(post("/test-support/items/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
