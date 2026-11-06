package uk.gov.justice.laa.datauserapi.security.opa;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.datauserapi.exception.OpaUnavailableException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OpaAuthorizationClientTest {

    @Test
    void postsAuthorizationInputToTheOpaDecisionEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/allow"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "input": {
                            "action": "deactivate",
                            "actor": {
                              "entra_oid": "actor-id",
                              "user_type": "INTERNAL",
                              "firm_id": null,
                              "silas_role_identifiers": ["External User Admin"],
                              "permissions": ["DISABLE_EXTERNAL_USER"]
                            },
                            "target": {
                              "entra_oid": "target-id",
                              "user_type": "EXTERNAL",
                              "firm_id": "firm-id",
                              "multi_firm": false,
                              "active": true,
                              "deactivation_type": null
                            }
                          }
                        }
                        """))
                .andRespond(withSuccess("{\"result\":true}", MediaType.APPLICATION_JSON));
        OpaAuthorizationClient client = new OpaAuthorizationClient(builder.build());

        assertThat(client.isAllowed(input())).isTrue();
        server.verify();
    }

    @Test
    void rejectsMalformedOpaResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/allow"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        OpaAuthorizationClient client = new OpaAuthorizationClient(builder.build());

        assertThatThrownBy(() -> client.isAllowed(input()))
                .isInstanceOf(OpaUnavailableException.class)
                .hasMessage("OPA authorization response did not contain a decision");
        server.verify();
    }

    @Test
    void reportsOpaServerErrorsAsUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/allow"))
                .andRespond(withServerError());
        OpaAuthorizationClient client = new OpaAuthorizationClient(builder.build());

        assertThatThrownBy(() -> client.isAllowed(input()))
                .isInstanceOf(OpaUnavailableException.class)
                .hasMessage("OPA authorization request failed");
        server.verify();
    }

    private static OpaAuthorizationInput input() {
        return new OpaAuthorizationInput(
                "deactivate",
                new OpaAuthorizationInput.Actor(
                        "actor-id",
                        "INTERNAL",
                        null,
                        Set.of("External User Admin"),
                        Set.of("DISABLE_EXTERNAL_USER")),
                new OpaAuthorizationInput.Target(
                        "target-id",
                        "EXTERNAL",
                        "firm-id",
                        false,
                        true,
                        null));
    }
}
