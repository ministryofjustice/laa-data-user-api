package uk.gov.justice.laa.datauserapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.justice.laa.datauserapi.application.command.service.UserAccountCreationService;
import uk.gov.justice.laa.datauserapi.client.ts.EntraUserClient;
import uk.gov.justice.laa.datauserapi.config.CorrelationIdFilter;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResult;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyExistsException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:create-user-schema.sql"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CreateUserAccountIntegrationTest extends BaseIntegrationTest {
    private static final String PATH = "/api/v1/commands/users/create";
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountCreationService service;
    @MockitoBean private EntraUserClient client;
    @MockitoBean(name = "jwtDecoder") private JwtDecoder decoder;
    private MockMvc mvc;
    private UUID actorId;
    private UUID actorOid;
    private UUID actorProfile;
    private UUID firm;
    private UUID role;
    private UUID identity;
    private String email;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(CorrelationIdFilter.class))
                .apply(springSecurity()).build();
        actorId = UUID.randomUUID();
        actorOid = UUID.randomUUID();
        actorProfile = UUID.randomUUID();
        firm = UUID.randomUUID();
        role = UUID.randomUUID();
        identity = UUID.randomUUID();
        email = UUID.randomUUID() + "@example.com";
        UUID app = UUID.randomUUID();
        jdbc.update("insert into firm (id, name, type, enabled) values (?, ?, 'LEGAL_SERVICES_PROVIDER', true)",
                firm, "Firm " + firm);
        jdbc.update("""
                insert into app (id, name, description, app_type, ordinal, url, enabled, security_group_oid)
                values (?, ?, ?, 'AUTHZ', 0, ?, true, ?)
                """, app, "App " + app, "Description " + app, "https://example.com/" + app, app.toString());
        jdbc.update("""
                insert into app_role (id, name, description, ordinal, legacy_sync, authz_role, app_id)
                values (?, 'Internal admin', 'Internal admin', 0, false, true, ?)
                """, role, app);
        jdbc.update("""
                insert into entra_user (id, entra_oid, email, first_name, last_name, status, multi_firm_user,
                enabled, mail_only, ccms_ebs_user, created_date, created_by)
                values (?, ?, ?, 'Admin', 'User', 'ACTIVE', false, true, false, false, current_timestamp, 'fixture')
                """, actorId, actorOid.toString(), actorOid + "@example.com");
        jdbc.update("""
                insert into user_profile (id, entra_user_id, active_profile, user_type, status, silas_status,
                last_sync_successful, unrestricted_office_access, created_date, created_by)
                values (?, ?, true, 'INTERNAL', 'COMPLETE', 'COMPLETE', false, false, current_timestamp, 'fixture')
                """, actorProfile, actorId);
        jdbc.update("insert into user_profile_app_role (user_profile_id, app_role_id) values (?, ?)", actorProfile, role);
        jdbc.update("insert into role_permission (app_role_id, permission) values (?, 'CREATE_EXTERNAL_USER')", role);
    }

    private Map<String, Object> body() {
        return new LinkedHashMap<>(Map.of("email", email, "firstName", "First", "lastName", "Last",
                "userType", "EXTERNAL", "multiFirmUser", true, "firmId", firm.toString()));
    }

    private CreateUserAccountCommand command() {
        return new CreateUserAccountCommand(email, "First", "Last", "EXTERNAL", true, firm.toString());
    }

    private MockHttpServletRequestBuilder authorizedPost(Map<String, Object> body) throws Exception {
        return post(PATH).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))
                .header("Authorization", "Bearer " + stubToken("user_data.admin"));
    }

    private String stubToken(String scope) {
        String token = UUID.randomUUID().toString();
        when(decoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "RS256")
                .subject("subject-is-not-the-actor-oid")
                .claim("oid", actorOid.toString()).claim("scp", scope)
                .claim("roles", "CREATE_EXTERNAL_USER").build());
        return token;
    }

    @Test
    void createsInitialProfileAndRetryReturnsExistingIdsWithoutProvisioningAgain() throws Exception {
        when(client.createUser(any())).thenReturn(new EntraUserClient.Identity(identity, true));
        var result = mvc.perform(authorizedPost(body()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.userEntraObjectId").value(identity.toString()))
                .andExpect(jsonPath("$.userProfileId").isNotEmpty())
                .andExpect(header().exists("X-Correlation-ID")).andReturn();
        UUID profile = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("userProfileId").asText());
        var stored = jdbc.queryForMap("""
                select eu.multi_firm_user, eu.enabled, eu.status as account_status, eu.created_by as account_creator,
                up.firm_id, up.user_type, up.status, up.silas_status, up.active_profile, up.created_by,
                up.unrestricted_office_access
                from entra_user eu join user_profile up on up.entra_user_id=eu.id where up.id=?
                """, profile);
        assertThat(stored).containsEntry("multi_firm_user", true).containsEntry("enabled", true)
                .containsEntry("account_status", "ACTIVATION_REQUIRED").containsEntry("account_creator", actorOid.toString())
                .containsEntry("firm_id", firm).containsEntry("user_type", "EXTERNAL").containsEntry("status", "PENDING")
                .containsEntry("silas_status", "ACTIVATION_REQUIRED").containsEntry("active_profile", true)
                .containsEntry("created_by", actorOid.toString()).containsEntry("unrestricted_office_access", false);
        assertThat(jdbc.queryForObject("select count(*) from user_profile_app_role where user_profile_id=?", Integer.class, profile)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_profile_office where user_profile_id=?", Integer.class, profile)).isZero();
        var retry = body();
        retry.put("email", email.toUpperCase(java.util.Locale.ROOT));
        mvc.perform(authorizedPost(retry)).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.userEntraObjectId").value(identity.toString()))
                .andExpect(jsonPath("$.userProfileId").value(profile.toString()));
        verify(client, times(1)).createUser(any());
    }

    @Test
    void singleFirmFlagIsPersisted() throws Exception {
        when(client.createUser(any())).thenReturn(new EntraUserClient.Identity(identity, true));
        var body = body();
        body.put("multiFirmUser", false);
        mvc.perform(authorizedPost(body)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select multi_firm_user from entra_user where entra_oid=?",
                Boolean.class, identity.toString())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "firstName", "lastName", "userType", "multiFirmUser", "firmId"})
    void missingRequiredFieldIsRejectedBeforeSideEffects(String field) throws Exception {
        var body = body();
        body.remove(field);
        mvc.perform(authorizedPost(body)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(client);
        assertNoLocalUser();
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "firstName", "lastName", "userType", "firmId"})
    void invalidFieldIsRejectedBeforeSideEffects(String field) throws Exception {
        var body = body();
        body.put(field, switch (field) {
            case "email" -> "invalid-email";
            case "userType" -> "INTERNAL";
            case "firmId" -> "1-1-1-1-1";
            default -> " ";
        });
        mvc.perform(authorizedPost(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(client);
        assertNoLocalUser();
    }

    @Test
    void unknownFirmIsFieldLevelBadRequest() throws Exception {
        var body = body();
        body.put("firmId", UUID.randomUUID().toString());
        mvc.perform(authorizedPost(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("firmId"));
        verifyNoInteractions(client);
        assertNoLocalUser();
    }

    @Test
    void malformedJsonIsBadRequestRatherThanInternalError() throws Exception {
        mvc.perform(authorizedPost(body()).content("{")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"external", "inactive", "disabled", "noPermission", "notAuthzRole"})
    void rejectsActorsWithoutInternalDatabasePermissionDespiteForgedRoleClaims(String mode) throws Exception {
        switch (mode) {
            case "external" -> jdbc.update("update user_profile set user_type='EXTERNAL', firm_id=? where id=?", firm, actorProfile);
            case "inactive" -> jdbc.update("update user_profile set active_profile=false where id=?", actorProfile);
            case "disabled" -> jdbc.update("update entra_user set enabled=false where id=?", actorId);
            case "noPermission" -> jdbc.update("delete from role_permission where app_role_id=?", role);
            case "notAuthzRole" -> jdbc.update("update app_role set authz_role=false where id=?", role);
            default -> throw new AssertionError(mode);
        }
        mvc.perform(authorizedPost(body()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(client);
        assertNoLocalUser();
    }

    @Test
    void requiresAdminScope() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body()))
                        .header("Authorization", "Bearer " + stubToken("user_data.read")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(client);
    }

    @Test
    void roleClaimDoesNotReplaceMissingScope() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body()))
                        .header("Authorization", "Bearer " + stubToken("")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(client);
        assertNoLocalUser();
    }

    @Test
    void unknownActorIsRejectedByBearerAuthentication() throws Exception {
        String token = UUID.randomUUID().toString();
        when(decoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "RS256")
                .claim("oid", UUID.randomUUID().toString()).claim("scp", "user_data.admin").build());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        verify(decoder).decode(token);
        verifyNoInteractions(client);
        assertNoLocalUser();
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(client);
    }

    @Test
    void rejectedSignedTokenUsesSameProblemResponse() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("private validation detail"));
        mvc.perform(post(PATH).header("Authorization", "Bearer invalid").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().exists("X-Correlation-ID"));
        verifyNoInteractions(client);
        verify(decoder).decode("invalid");
    }

    @Test
    void getIsMethodNotAllowedRatherThanInternalError() throws Exception {
        mvc.perform(get(PATH).header("Authorization", "Bearer " + stubToken("user_data.admin")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(ints = {409, 502, 503, 504})
    void downstreamFailuresAreProblemDetailsWithoutLocalWrites(int status) throws Exception {
        when(client.createUser(any())).thenThrow(new CreateUserException(HttpStatus.valueOf(status), "Identity provider failed"));
        mvc.perform(authorizedPost(body())).andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
        assertNoLocalUser();
    }

    @Test
    void existingDownstreamIdentityReturnsConflictWithoutInventingProfileId() throws Exception {
        when(client.createUser(any())).thenReturn(new EntraUserClient.Identity(identity, false));
        mvc.perform(authorizedPost(body())).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.userEntraObjectId").value(identity.toString()))
                .andExpect(jsonPath("$.userProfileId").doesNotExist());
        assertNoLocalUser();
    }

    @Test
    void profileInsertFailureRollsBackUserAndLogsRetainedIdentityWithCorrelation(CapturedOutput output) throws Exception {
        when(client.createUser(any())).thenAnswer(invocation -> {
            jdbc.update("delete from firm where id=?", firm);
            return new EntraUserClient.Identity(identity, true);
        });
        var response = mvc.perform(authorizedPost(body())).andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The user could not be stored locally")).andReturn().getResponse();
        assertNoLocalUser();
        assertThat(output.getOut()).contains("Identity retained; manual reconciliation required",
                "identityId=" + identity, "correlationId=" + response.getHeader("X-Correlation-ID"),
                "step=local_constraint", "system=PostgreSQL");
        assertThat(response.getContentAsString()).doesNotContain("insert into", "foreign key", email);
        verify(client).createUser(any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void deferredCommitFailureRollsBackBothRowsAndLogsRetainedIdentity(CapturedOutput output) throws Exception {
        jdbc.execute("""
                create function reject_user_creation_commit() returns trigger language plpgsql as $$
                begin raise exception 'Simulated private commit failure'; end; $$
                """);
        jdbc.execute("""
                create constraint trigger reject_user_creation_commit after insert on entra_user
                deferrable initially deferred for each row when (new.entra_oid = '%s')
                execute function reject_user_creation_commit()
                """.formatted(identity));
        try {
            when(client.createUser(any())).thenReturn(new EntraUserClient.Identity(identity, true));
            var response = mvc.perform(authorizedPost(body())).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("The user could not be stored locally"))
                    .andReturn().getResponse();
            assertNoLocalUser();
            assertThat(output.getOut()).contains("Identity retained; manual reconciliation required",
                    "identityId=" + identity, "correlationId=" + response.getHeader("X-Correlation-ID"));
            assertThat(response.getContentAsString()).doesNotContain("Simulated private commit failure");
        } finally {
            jdbc.execute("drop trigger reject_user_creation_commit on entra_user");
            jdbc.execute("drop function reject_user_creation_commit()");
        }
    }

    @Test
    void concurrentCaseInsensitiveCreatesCommitOnlyOneLocalUserAndProfile() throws Exception {
        var barrier = new CyclicBarrier(2);
        when(client.createUser(any())).thenAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return new EntraUserClient.Identity(UUID.randomUUID(), true);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createOrConflict(command()));
            var secondCommand = new CreateUserAccountCommand(email.toUpperCase(java.util.Locale.ROOT),
                    "First", "Last", "EXTERNAL", true, firm.toString());
            var second = executor.submit(() -> createOrConflict(secondCommand));
            var results = java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(results).extracting(CreateUserAccountResult::success).containsExactlyInAnyOrder(true, false);
            assertThat(results.getFirst().userEntraObjectId()).isEqualTo(results.getLast().userEntraObjectId());
        }
        assertThat(jdbc.queryForObject("select count(*) from entra_user where lower(email)=?", Integer.class, email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from user_profile up join entra_user eu on eu.id=up.entra_user_id
                where lower(eu.email)=?
                """, Integer.class, email)).isEqualTo(1);
    }

    private CreateUserAccountResult createOrConflict(CreateUserAccountCommand request) {
        try {
            return service.create(request, actorOid.toString());
        } catch (UserAlreadyExistsException ex) {
            return ex.result();
        }
    }

    private void assertNoLocalUser() {
        assertThat(jdbc.queryForObject("select count(*) from entra_user where lower(email)=?", Integer.class, email)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from user_profile up join entra_user eu on eu.id=up.entra_user_id
                where lower(eu.email)=?
                """, Integer.class, email)).isZero();
    }
}
