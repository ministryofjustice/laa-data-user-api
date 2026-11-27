package uk.gov.justice.laa.datauserapi.application.query.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.datauserapi.application.query.service.CurrentUserPermissionsService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuthenticatedEntraUserQueryRepository;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.config.JacksonConfig;
import uk.gov.justice.laa.datauserapi.config.SecurityConfig;
import uk.gov.justice.laa.datauserapi.security.opa.OpaAuthorizationClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CurrentUserPermissionsController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JacksonConfig.class, JwtToAuthenticatedEntraUserConverter.class,
    CurrentUserPermissionsService.class, OpaAuthorizationClient.class,
    CurrentUserPermissionsControllerTest.OpaTestConfiguration.class})
class CurrentUserPermissionsControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MockRestServiceServer opa;
    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private AuthenticatedEntraUserQueryRepository users;

    @BeforeEach
    void resetServer() {
        opa.reset();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user_data.read", "user_data.admin"})
    void returnsOpaPermissionsUsingOnlyValidatedJwtRoles(String scope) throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Global Admin", "External User Manager"), scope));
        opa.expect(requestTo("http://opa/v1/data/authz/permissions/caller_permissions"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"input":{"roles":["Global Admin","External User Manager"]}}
                        """))
                .andRespond(withSuccess("""
                        {"result":{"allow":true,"permissions":["VIEW_EXTERNAL_USER","EDIT_EXTERNAL_USER"]}}
                        """, MediaType.APPLICATION_JSON));

        var result = mvc.perform(get("/current-user/permissions").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.permissions[0]").value("EDIT_EXTERNAL_USER"))
                .andExpect(jsonPath("$.permissions[1]").value("VIEW_EXTERNAL_USER"))
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        verify(decoder).decode("valid");
        verifyNoInteractions(users);
        opa.verify();
    }

    @Test
    void deniedRolesReturnGeneric403WithoutQueryingDatabase() throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Unknown Role"), "user_data.read"));
        opa.expect(requestTo("http://opa/v1/data/authz/permissions/caller_permissions"))
                .andRespond(withSuccess("""
                        {"result":{"allow":false,"permissions":[]}}
                        """, MediaType.APPLICATION_JSON));

        mvc.perform(get("/current-user/permissions").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "You do not possess the required scopes or permissions to perform this operation."))
                .andExpect(jsonPath("$.permissions").doesNotExist());

        verifyNoInteractions(users);
        opa.verify();
    }

    @Test
    void requestParametersCannotOverrideJwtRolesOrSelectAnotherCaller() throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Internal User Viewer"), "user_data.read"));
        opa.expect(requestTo("http://opa/v1/data/authz/permissions/caller_permissions"))
                .andExpect(content().json("""
                        {"input":{"roles":["Internal User Viewer"]}}
                        """))
                .andRespond(withSuccess("""
                        {"result":{"allow":true,"permissions":["VIEW_AUDIT_TABLE","VIEW_INTERNAL_USER"]}}
                        """, MediaType.APPLICATION_JSON));

        mvc.perform(get("/current-user/permissions").header("Authorization", "Bearer valid")
                        .param("roles", "Global Admin").param("userId", "another-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(2));
        verifyNoInteractions(users);
        opa.verify();
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("malformedRoles")
    void missingOrMalformedRolesAreDeniedWithoutCallingOpaOrDatabase(Object roles) throws Exception {
        when(decoder.decode("valid")).thenReturn(token(roles, "user_data.read"));

        mvc.perform(get("/current-user/permissions").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(users);
        opa.verify();
    }

    static Stream<Object> malformedRoles() {
        return Stream.of("Global Admin", 1, true, Map.of("role", "Global Admin"),
                List.of("Global Admin", 1));
    }

    @Test
    void missingTokenReturns401WithoutCallingOpaOrDatabase() throws Exception {
        mvc.perform(get("/current-user/permissions")).andExpect(status().isUnauthorized());
        verifyNoInteractions(decoder, users);
        opa.verify();
    }

    @Test
    void invalidTokenReturns401WithoutCallingOpaOrDatabase() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid signature"));
        mvc.perform(get("/current-user/permissions").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
        opa.verify();
    }

    @Test
    void rolesDoNotReplaceRequiredOauthScope() throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Global Admin"), "unrelated"));
        mvc.perform(get("/current-user/permissions").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
        opa.verify();
    }

    @Test
    void postCannotUseTheGetOnlyEndpoint() throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Global Admin"), "user_data.read"));
        mvc.perform(post("/current-user/permissions").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
        opa.verify();
    }

    @Test
    void postWithCsrfTokenIsAlsoDenied() throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Global Admin"), "user_data.read"));
        mvc.perform(post("/current-user/permissions").header("Authorization", "Bearer " + "valid").with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
        opa.verify();
    }

    @Test
    void otherEndpointsStillUseDatabaseBackedAuthentication() throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Global Admin"), "user_data.read"));
        when(users.findByEntraOid("actor-oid")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/queries/firms").header("Authorization", "Bearer valid"))
                .andExpect(status().isUnauthorized());

        verify(users).findByEntraOid("actor-oid");
        opa.verify();
    }

    @Test
    void opaOutageReturns503WithoutDatabaseFallback() throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Global Admin"), "user_data.read"));
        opa.expect(requestTo("http://opa/v1/data/authz/permissions/caller_permissions"))
                .andRespond(withServerError());

        mvc.perform(get("/current-user/permissions").header("Authorization", "Bearer valid"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The authorization service is temporarily unavailable."));
        verifyNoInteractions(users);
        opa.verify();
    }

    @Test
    void malformedOpaDecisionReturns503InsteadOfPermissions() throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Global Admin"), "user_data.read"));
        opa.expect(requestTo("http://opa/v1/data/authz/permissions/caller_permissions"))
                .andRespond(withSuccess("""
                        {"result":{"allow":"true","permissions":["VIEW_INTERNAL_USER"]}}
                        """, MediaType.APPLICATION_JSON));
        mvc.perform(get("/current-user/permissions").header("Authorization", "Bearer valid"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.permissions").doesNotExist());
        verifyNoInteractions(users);
        opa.verify();
    }

    @Test
    void oversizedOpaDecisionReturns503InsteadOfPermissions() throws Exception {
        when(decoder.decode("valid")).thenReturn(token(List.of("Global Admin"), "user_data.read"));
        String permissions = IntStream.range(0, 101)
                .mapToObj(index -> "\"PERMISSION_" + index + "\"")
                .collect(Collectors.joining(","));
        opa.expect(requestTo("http://opa/v1/data/authz/permissions/caller_permissions"))
                .andRespond(withSuccess("{\"result\":{\"allow\":true,\"permissions\":[" + permissions + "]}}",
                        MediaType.APPLICATION_JSON));

        mvc.perform(get("/current-user/permissions").header("Authorization", "Bearer " + "valid"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.permissions").doesNotExist());
        verifyNoInteractions(users);
        opa.verify();
    }

    private static Jwt token(Object roles, String scope) {
        Jwt.Builder builder = Jwt.withTokenValue("valid").header("alg", "RS256")
                .subject("actor").claim("oid", "actor-oid").claim("scp", scope);
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OpaTestConfiguration {
        private final RestClient.Builder builder = RestClient.builder().baseUrl("http://opa");
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        @Bean
        RestClient opaRestClient() {
            return builder.build();
        }

        @Bean
        MockRestServiceServer opaServer() {
            return server;
        }
    }
}
