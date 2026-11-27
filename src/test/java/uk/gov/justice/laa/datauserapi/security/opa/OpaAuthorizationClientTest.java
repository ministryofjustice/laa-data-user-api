package uk.gov.justice.laa.datauserapi.security.opa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.datauserapi.exception.OpaUnavailableException;

import java.util.Set;
import java.util.List;
import java.util.stream.IntStream;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OpaAuthorizationClientTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "null", "true", "[]", "{}", "{\"allow\":true}",
        "{\"allow\":\"true\",\"reason\":\"ALLOWED\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[\"EXTERNAL\"],\"roleIds\":[]}}",
        "{\"allow\":true,\"reason\":\"ALLOWED\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[],\"roleIds\":[]}}",
        "{\"allow\":true,\"reason\":\"ALLOWED\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[\"UNKNOWN\"],\"roleIds\":[]}}",
        "{\"allow\":true,\"reason\":\"ALLOWED\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[1],\"roleIds\":[]}}",
        "{\"allow\":true,\"reason\":\"ALLOWED\",\"queryFilters\":{\"firmId\":\"1-1-1-1-1\",\"userTypes\":[\"EXTERNAL\"],\"roleIds\":[]}}",
        "{\"allow\":true,\"reason\":\"ALLOWED\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[\"EXTERNAL\"],\"roleIds\":[\"00000000-0000-0000-0000-000000000001\"]}}",
        "{\"allow\":true,\"reason\":\"ALLOWED\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[\"EXTERNAL\"],\"roleIds\":[],\"newConstraint\":true}}",
        "{\"allow\":true,\"reason\":\"MISSING_PERMISSION\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[\"EXTERNAL\"],\"roleIds\":[]}}",
        "{\"allow\":false,\"reason\":\"ALLOWED\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[],\"roleIds\":[]}}",
        "{\"allow\":false,\"reason\":\"MISSING_PERMISSION\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[\"EXTERNAL\"],\"roleIds\":[]}}",
        "{\"allow\":false,\"reason\":\"UNKNOWN\",\"queryFilters\":{\"firmId\":null,\"userTypes\":[],\"roleIds\":[]}}"
    })
    void malformedProfileDecisionsFailClosed(String result) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/trial"))
                .andRespond(withSuccess("{\"result\":" + result + "}", MediaType.APPLICATION_JSON));
        var actor = new uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput.Actor(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "actor",
                uk.gov.justice.laa.datauserapi.contracts.domain.UserType.INTERNAL, null, Set.of(), Set.of(), Set.of(), true);
        var input = new uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput(
                uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialOperation.SEARCH,
                actor, null, java.util.Map.of(), Set.of(), Set.of(), true);
        assertThatThrownBy(() -> new OpaAuthorizationClient(builder.build()).profileDecision(input))
                .isInstanceOf(OpaUnavailableException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "not-json", "{\"result\":",
        "null", "true", "[]", "{}", "{\"result\":null}", "{\"result\":true}",
        "{\"result\":[]}", "{\"result\":{}}",
        "{\"result\":{\"allow\":\"true\",\"permissions\":[\"VIEW_EXTERNAL_USER\"]}}",
        "{\"result\":{\"allow\":1,\"permissions\":[\"VIEW_EXTERNAL_USER\"]}}",
        "{\"result\":{\"permissions\":[\"VIEW_EXTERNAL_USER\"]}}",
        "{\"result\":{\"allow\":true}}",
        "{\"result\":{\"allow\":true,\"permissions\":null}}",
        "{\"result\":{\"allow\":true,\"permissions\":\"VIEW_EXTERNAL_USER\"}}",
        "{\"result\":{\"allow\":true,\"permissions\":[]}}",
        "{\"result\":{\"allow\":false,\"permissions\":[\"VIEW_EXTERNAL_USER\"]}}",
        "{\"result\":{\"allow\":true,\"permissions\":[null]}}",
        "{\"result\":{\"allow\":true,\"permissions\":[1]}}",
        "{\"result\":{\"allow\":true,\"permissions\":[true]}}",
        "{\"result\":{\"allow\":true,\"permissions\":[{}]}}",
        "{\"result\":{\"allow\":true,\"permissions\":[\"\"]}}",
        "{\"result\":{\"allow\":true,\"permissions\":[\"view_external_user\"]}}",
        "{\"result\":{\"allow\":true,\"permissions\":[\"VIEW_EXTERNAL_USER\",\" bad \"]}}"
    })
    void rejectsMalformedCallerPermissionsDecisions(String response) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/permissions/caller_permissions"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        OpaAuthorizationClient client = new OpaAuthorizationClient(builder.build());

        assertThatThrownBy(() -> client.callerPermissions(List.of("Global Admin")))
                .isInstanceOf(OpaUnavailableException.class);
        server.verify();
    }

    @Test
    void callerPermissionsAreDeduplicatedAndSorted() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/permissions/caller_permissions"))
                .andRespond(withSuccess("""
                        {"result":{"allow":true,"permissions":[
                          "VIEW_EXTERNAL_USER","EDIT_EXTERNAL_USER","VIEW_EXTERNAL_USER"]}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(new OpaAuthorizationClient(builder.build()).callerPermissions(List.of("Global Admin")))
                .hasValue(List.of("EDIT_EXTERNAL_USER", "VIEW_EXTERNAL_USER"));
        server.verify();
    }

    @Test
    void acceptsTheMaximumNumberOfDistinctCallerPermissions() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/permissions/caller_permissions"))
                .andRespond(withSuccess(permissionsResponse(100), MediaType.APPLICATION_JSON));

        assertThat(new OpaAuthorizationClient(builder.build()).callerPermissions(List.of("Global Admin")))
                .hasValueSatisfying(permissions -> assertThat(permissions).hasSize(100));
        server.verify();
    }

    @Test
    void rejectsCallerPermissionsAboveTheResponseLimit() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/permissions/caller_permissions"))
                .andRespond(withSuccess(permissionsResponse(101), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new OpaAuthorizationClient(builder.build()).callerPermissions(List.of("Global Admin")))
                .isInstanceOf(OpaUnavailableException.class);
        server.verify();
    }

    private static String permissionsResponse(int count) {
        String permissions = IntStream.range(0, count)
                .mapToObj(index -> "\"PERMISSION_" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"result\":{\"allow\":true,\"permissions\":[" + permissions + "]}}";
    }

    @Test
    void callerPermissionsTimeoutFailsClosed() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/permissions/caller_permissions"))
                .andRespond(withException(new SocketTimeoutException("timed out")));

        assertThatThrownBy(() -> new OpaAuthorizationClient(builder.build())
                .callerPermissions(List.of("Global Admin")))
                .isInstanceOf(OpaUnavailableException.class)
                .hasCauseInstanceOf(org.springframework.web.client.ResourceAccessException.class);
        server.verify();
    }

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

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"result\":null}",
        "{\"result\":\"true\"}",
        "{\"result\":\"false\"}",
        "{\"result\":1}",
        "{\"result\":0}",
        "{\"result\":1.0}",
        "{\"result\":{}}",
        "{\"result\":[]}",
        "{\"result\":[true]}",
        "null",
        "true",
        "\"true\"",
        "1",
        "[]",
        "[{\"result\":true}]"
    })
    void rejectsNonBooleanOpaDecisions(String response) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/allow"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        OpaAuthorizationClient client = new OpaAuthorizationClient(builder.build());

        assertThatThrownBy(() -> client.isAllowed(input()))
                .isInstanceOf(OpaUnavailableException.class)
                .hasMessage("OPA authorization response did not contain a boolean decision");
        server.verify();
    }

    @Test
    void returnsFalseForAnOpaDenial() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/allow"))
                .andRespond(withSuccess("{\"result\":false}", MediaType.APPLICATION_JSON));
        OpaAuthorizationClient client = new OpaAuthorizationClient(builder.build());

        assertThat(client.isAllowed(input())).isFalse();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{\"result\":", "not-json"})
    void rejectsInvalidJsonResponses(String response) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8181");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8181/v1/data/authz/allow"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        OpaAuthorizationClient client = new OpaAuthorizationClient(builder.build());

        assertThatThrownBy(() -> client.isAllowed(input()))
                .isInstanceOf(OpaUnavailableException.class);
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
