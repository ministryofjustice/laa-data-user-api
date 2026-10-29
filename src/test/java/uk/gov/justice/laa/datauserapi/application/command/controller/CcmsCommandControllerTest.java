package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.datauserapi.application.command.ccms.TriggerCcmsRoleSyncCommand;
import uk.gov.justice.laa.datauserapi.application.command.handler.TriggerCcmsRoleSyncHandler;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.config.SecurityConfig;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;
import org.springframework.security.oauth2.jwt.BadJwtException;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(CcmsCommandController.class)
@Import({SecurityConfig.class, CcmsCommandController.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class CcmsCommandControllerTest {

    private static final String ENDPOINT = "/api/v1/commands/ccms/trigger-role-sync";
    private static final AuthenticatedEntraUser ACTOR = AuthenticatedEntraUser.builder()
            .id(UUID.randomUUID()).entraOid("actor-oid").firstName("First").lastName("Last").build();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TriggerCcmsRoleSyncHandler handler;
    @MockitoBean
    private JwtToAuthenticatedEntraUserConverter converter;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void systemAdminReceivesHandlerResultUsingAuthenticatedActor(boolean accepted) throws Exception {
        CommandResult expected = new CommandResult(accepted, accepted ? "Accepted" : "Worker busy");
        when(handler.handle(new TriggerCcmsRoleSyncCommand(), ACTOR)).thenReturn(expected);

        mockMvc.perform(post(ENDPOINT).with(authentication(authenticated("system.admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(accepted))
                .andExpect(jsonPath("$.userProfileCount").doesNotExist())
                .andExpect(jsonPath("$.message").value(expected.message()));

        verify(handler).handle(new TriggerCcmsRoleSyncCommand(), ACTOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user_data.admin", "user_data.read", ""})
    void otherScopesCannotStartSync(String scope) throws Exception {
        mockMvc.perform(post(ENDPOINT).with(authentication(authenticated(scope))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.instance").value(ENDPOINT));
        verifyNoInteractions(handler);
    }

    @Test
    void unauthenticatedRequestCannotStartSync() throws Exception {
        mockMvc.perform(post(ENDPOINT)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value(ENDPOINT));
        verifyNoInteractions(handler);
    }

    @Test
    void invalidTokenReceivesUnauthorizedProblemDetail() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Invalid token"));
        mockMvc.perform(post(ENDPOINT).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value(ENDPOINT));
        verifyNoInteractions(handler);
    }

    private UsernamePasswordAuthenticationToken authenticated(String scope) {
        return new UsernamePasswordAuthenticationToken(ACTOR, null,
                List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
