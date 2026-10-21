package uk.gov.justice.laa.datauserapi.client.ts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TechServicesEntraUserClientTest {
    private static final String DEFAULT_GROUP = "22222222-2222-2222-2222-222222222222";
    private MockRestServiceServer server;
    private RestClient restClient;
    private TechServicesEntraUserClient client;
    private final UUID id = UUID.randomUUID();
    private final CreateUserAccountCommand command = new CreateUserAccountCommand(
            "person@example.com", "First", "Last", "EXTERNAL", true, UUID.randomUUID().toString());

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://tech-services.example");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new TechServicesEntraUserClient(restClient, new ObjectMapper(), () -> "test-token",
                "LAA", "activation_code_email", DEFAULT_GROUP, true);
    }

    private String response() {
        return "{\"success\":true,\"user\":{\"id\":\"" + id + "\"}}";
    }

    @Test
    void registersWithDefaultAccessGroupWithoutRoleOrOfficeAssignments() {
        server.expect(requestTo("https://tech-services.example/LAA/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().json("""
                        {"givenName":"First","surname":"Last","mail":"person@example.com",
                         "verification_method":"activation_code_email","groups":["22222222-2222-2222-2222-222222222222"]}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(response()));
        assertThat(client.createUser(command)).isEqualTo(new EntraUserClient.Identity(id, true));
        server.verify();
    }

    @Test
    void recognisesExistingIdentityOnRetry() {
        server.expect(anything()).andRespond(withSuccess(response(), MediaType.APPLICATION_JSON));
        assertThat(client.createUser(command)).isEqualTo(new EntraUserClient.Identity(id, false));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "not json", "null", "", "{\"success\":false}", "{\"success\":true,\"user\":{}}",
        "{\"success\":true,\"user\":{\"id\":\"bad-id\"}}", "{\"success\":true,\"user\":{\"id\":\"1-1-1-1-1\"}}"})
    void invalidSuccessPayloadIsBadGateway(String body) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.CREATED).body(body));
        assertStatus(502);
        server.verify();
    }

    @Test
    void rejectsTrailingDataRatherThanAcceptingPartialJson() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.CREATED).body(response() + " {}"));
        assertStatus(502);
    }

    @Test
    void missingAuthenticationTokenIsBadGatewayWithoutCallingDownstream() {
        client = new TechServicesEntraUserClient(restClient, new ObjectMapper(), () -> null,
                "LAA", "activation_code_email", DEFAULT_GROUP, true);
        assertStatus(502);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {202, 204, 400, 401, 403, 404, 429, 500, 503, 504})
    void unexpectedDownstreamStatusesAreBadGateway(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).body("PRIVATE DOWNSTREAM PAYLOAD"));
        assertStatus(502);
        server.verify();
    }

    @Test
    void rawConflictIsAnExplicitConflictWithoutLeakingResponse() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.CONFLICT).body("PRIVATE DOWNSTREAM PAYLOAD"));
        assertStatus(409);
        server.verify();
    }

    @Test
    void unreachableServiceIsUnavailable() {
        server.expect(anything()).andRespond(withException(new ConnectException("private host")));
        assertStatus(503);
    }

    @Test
    void configuredNetworkTimeoutIsGatewayTimeout() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("private host")));
        assertStatus(504);
    }

    @Test
    void disabledIntegrationFailsExplicitlyWithoutInventingAnIdentity() {
        client = new TechServicesEntraUserClient(restClient, new ObjectMapper(),
                () -> {
                    throw new AssertionError("Token must not be requested");
                }, "LAA", "activation_code_email", DEFAULT_GROUP, false);
        assertStatus(503);
        server.verify();
    }

    @Test
    void authenticationFailureIsUnavailable() {
        client = new TechServicesEntraUserClient(restClient, new ObjectMapper(),
                () -> {
                    throw new IllegalStateException("private credentials");
                }, "LAA", "activation_code_email", DEFAULT_GROUP, true);
        assertStatus(503);
    }

    @Test
    void nestedAuthenticationTimeoutIsGatewayTimeout() {
        client = new TechServicesEntraUserClient(restClient, new ObjectMapper(),
                () -> {
                    throw new IllegalStateException(new java.util.concurrent.TimeoutException());
                },
                "LAA", "activation_code_email", DEFAULT_GROUP, true);
        assertStatus(504);
    }

    @Test
    void missingDefaultAccessGroupFailsBeforeTokenOrUserCreation() {
        client = new TechServicesEntraUserClient(restClient, new ObjectMapper(),
                () -> {
                    throw new AssertionError("Token must not be requested");
                }, "LAA", "activation_code_email", "", true);
        assertStatus(503);
        server.verify();
    }

    private void assertStatus(int expected) {
        assertThatThrownBy(() -> client.createUser(command)).isInstanceOfSatisfying(CreateUserException.class,
                ex -> {
                    assertThat(ex.status().value()).isEqualTo(expected);
                    assertThat(ex.getMessage()).doesNotContain("PRIVATE", "private");
                });
    }
}
