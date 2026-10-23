package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.datauserapi.application.command.handler.RefreshSilasStatusHandler;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.RefreshSilasStatusCommand;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.config.SecurityConfig;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileCommandController.class)
@Import({SecurityConfig.class, UserProfileCommandController.class})
@ActiveProfiles("test")
class UserProfileCommandControllerTest {

    private static final String ENDPOINT = "/api/v1/commands/user-profiles/refresh-silas-status";
    private static final String ACTOR = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RefreshSilasStatusHandler handler;
    @MockitoBean
    private JwtToAuthenticatedEntraUserConverter converter;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void systemAdminReceivesHandlerResultUsingAuthenticatedActor(boolean accepted) throws Exception {
        CommandResult expected = new CommandResult(accepted, accepted ? "Accepted" : "Worker busy");
        when(handler.handle(new RefreshSilasStatusCommand(), ACTOR)).thenReturn(expected);

        mockMvc.perform(post(ENDPOINT).with(authentication(authenticated("system.admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(accepted))
                .andExpect(jsonPath("$.message").value(expected.message()));

        verify(handler).handle(new RefreshSilasStatusCommand(), ACTOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user_data.admin", "user_data.read", ""})
    void otherScopesCannotStartRefresh(String scope) throws Exception {
        mockMvc.perform(post(ENDPOINT).with(authentication(authenticated(scope))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(handler);
    }

    @Test
    void unauthenticatedRequestCannotStartRefresh() throws Exception {
        mockMvc.perform(post(ENDPOINT)).andExpect(status().isUnauthorized());
        verifyNoInteractions(handler);
    }

    private UsernamePasswordAuthenticationToken authenticated(String scope) {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedEntraUser.builder().entraOid(ACTOR).build(), null,
                List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
