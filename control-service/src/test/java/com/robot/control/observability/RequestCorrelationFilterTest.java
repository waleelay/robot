package com.robot.control.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsSafeRequestIdDuringRequestAndEchoesItInResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "web-request-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isEqualTo("web-request-001"));

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER_NAME)).isEqualTo("web-request-001");
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "unsafe request\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY))
                        .matches("[A-Za-z0-9._-]{1,128}")
                        .isNotEqualTo("unsafe request\nvalue"));

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER_NAME))
                .matches("[A-Za-z0-9._-]{1,128}")
                .isNotEqualTo("unsafe request\nvalue");
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }
}
