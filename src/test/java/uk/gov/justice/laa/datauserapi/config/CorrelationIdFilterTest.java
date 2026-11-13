package uk.gov.justice.laa.datauserapi.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesRequestIdWhenHeaderIsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("otherContext", "retained");

        filter.doFilter(request, response, (req, res) -> {
            String id = MDC.get(CorrelationIdFilter.MDC_KEY);
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
            assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(id);
            assertThat(MDC.get("otherContext")).isEqualTo("retained");
        });

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        assertThat(MDC.get("otherContext")).isEqualTo("retained");
    }

    @ParameterizedTest
    @ValueSource(strings = {"upstream-request_123.v1", "550e8400-e29b-41d4-a716-446655440000"})
    void reusesValidIncomingIdInsteadOfPreviousThreadId(String incoming) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(CorrelationIdFilter.MDC_KEY, "previous-thread");

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(incoming));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(incoming);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("previous-thread");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "id with spaces", "id\r\ninjected", "id,value", "id/value"})
    void replacesInvalidIdsWithGeneratedUuid(String incoming) throws Exception {
        assertGeneratedReplacement(incoming);
    }

    @Test
    void enforcesLengthLimit() throws Exception {
        String boundary = "a".repeat(128);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, boundary);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(boundary));
        assertGeneratedReplacement("a".repeat(129));
        assertGeneratedReplacement(Character.toString(233));
    }

    @Test
    void replacesMultipleHeaderValues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "first");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "second");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY))).isNotNull());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotIn("first", "second");
    }

    private void assertGeneratedReplacement(String incoming) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            String id = MDC.get(CorrelationIdFilter.MDC_KEY);
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
            assertThat(id).isNotEqualTo(incoming);
            assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(id);
        });
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void restoresPreviousIdWhenRequestFails() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "previous");
        ServletException failure = new ServletException("request failed");

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {
                    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotEqualTo("previous");
                    throw failure;
                })).isSameAs(failure);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("previous");
    }

    @Test
    void successiveRequestsReceiveDifferentIds() throws Exception {
        AtomicReference<String> first = new AtomicReference<>();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> first.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotEqualTo(first.get()));
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
