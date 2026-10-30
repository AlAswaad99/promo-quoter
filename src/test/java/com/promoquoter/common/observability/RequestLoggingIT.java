package com.promoquoter.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.promoquoter.support.IntegrationTest;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void everyResponseCarriesAGeneratedRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader(RequestContext.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void incomingRequestIdIsEchoed() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health").header(RequestContext.HEADER, "client-req_1.7"))
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestContext.HEADER)).isEqualTo("client-req_1.7");
    }

    @Test
    void unsafeIncomingRequestIdIsReplaced() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/actuator/health").header(RequestContext.HEADER, "bad id with spaces"))
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestContext.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void errorBodyRequestIdMatchesResponseHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/does-not-exist").header(RequestContext.HEADER, "trace-me"))
                .andExpect(jsonPath("$.requestId").value("trace-me"))
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestContext.HEADER)).isEqualTo("trace-me");
    }

    @Test
    void differentRequestsGetDifferentRequestIds() throws Exception {
        String first = mockMvc.perform(get("/actuator/health")).andReturn().getResponse()
                .getHeader(RequestContext.HEADER);
        String second = mockMvc.perform(get("/actuator/health")).andReturn().getResponse()
                .getHeader(RequestContext.HEADER);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void logsStartAndCompletionWithStatusAndTimings(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/does-not-exist").header(RequestContext.HEADER, "log-check"));

        assertThat(output.getAll()).contains("[log-check]")
                .contains("event=\"http.request.started\"")
                .contains("event=\"http.request.completed\"")
                .contains("method=\"GET\"")
                .contains("path=\"/does-not-exist\"")
                .contains("status=\"404\"")
                .contains("startedAt=")
                .contains("endedAt=")
                .contains("durationMs=");
    }

    @Test
    void requestIdIsClearedAfterTheRequest() throws Exception {
        mockMvc.perform(get("/actuator/health"));

        assertThat(MDC.get(RequestContext.MDC_KEY)).isNull();
        assertThat(RequestContext.currentRequestId()).isEmpty();
    }
}
