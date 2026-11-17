package uk.gov.justice.laa.datauserapi.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CorrelationIdInterceptorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void configuredClientForwardsIncomingIdAndReadsContextForEachRequest() throws Exception {
        TechServicesProperties properties = new TechServicesProperties(null, null, null, null, "https://tech.example");
        RestClient.Builder builder = new TechServicesHttpConfig()
                .restClient(properties, new SimpleClientHttpRequestFactory()).mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://tech.example/first"))
                .andExpect(header(CorrelationIdFilter.HEADER_NAME, "upstream-123"))
                .andRespond(withSuccess());
        server.expect(requestTo("https://tech.example/second"))
                .andExpect(header(CorrelationIdFilter.HEADER_NAME, "next-request"))
                .andRespond(withSuccess());
        server.expect(requestTo("https://tech.example/no-context"))
                .andExpect(headerDoesNotExist(CorrelationIdFilter.HEADER_NAME))
                .andRespond(withSuccess());
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader(CorrelationIdFilter.HEADER_NAME, "upstream-123");
        RestClient client = builder.build();

        new CorrelationIdFilter().doFilter(incoming, new MockHttpServletResponse(), (request, response) ->
                client.patch().uri("/first").header(CorrelationIdFilter.HEADER_NAME, "stale")
                        .retrieve().toBodilessEntity());
        MDC.put(CorrelationIdFilter.MDC_KEY, "next-request");
        client.get().uri("/second").retrieve().toBodilessEntity();
        MDC.clear();
        client.get().uri("/no-context").retrieve().toBodilessEntity();

        server.verify();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
