package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileUnrestrictedOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserProfileOfficesMapper;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateOfficesUnrestrictedCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesRequest;
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

import java.util.Set;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(UserProfileCommandController.class)
@Import({SecurityConfig.class, UserProfileCommandController.class})
@ActiveProfiles("test")
class UserProfileCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Mock
    private UpdateUserProfileOfficesHandler updateUserProfileOfficesHandler;

    @Mock
    private UpdateUserProfileUnrestrictedOfficesHandler updateUserProfileUnrestrictedOfficesHandler;

    @MockitoBean
    private RefreshSilasStatusHandler handler;

    @Mock
    private UpdateUserProfileOfficesMapper mapper;

    @MockitoBean
    private JwtToAuthenticatedEntraUserConverter converter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UserProfileCommandController controller;

    private static final String ENDPOINT = "/api/v1/commands/user-profiles/refresh-silas-status";
    private static final AuthenticatedEntraUser ACTOR = AuthenticatedEntraUser.builder().id(UUID.randomUUID())
            .entraOid("actor-oid").firstName("first").lastName("last").build();

    @BeforeEach
    void setUp() {
        controller = new UserProfileCommandController(
                updateUserProfileOfficesHandler, handler, mapper, updateUserProfileUnrestrictedOfficesHandler);
    }

    @Test
    void updateOffices_dispatchesOfficeAssignmentsToHandler() {
        UUID profileId = UUID.randomUUID();
        String actorOid = UUID.randomUUID().toString();
        UpdateUserProfileOfficesRequest request = new UpdateUserProfileOfficesRequest(profileId, Set.of(UUID.randomUUID()));
        UpdateUserProfileOfficesCommand command = new UpdateUserProfileOfficesCommand(profileId, request.officeIds());
        CommandResult expected = CommandResult.success("offices updated");
        AuthenticatedEntraUser authenticatedUser = authenticatedUser(actorOid);
        when(mapper.toUpdateUserProfileOfficesCommand(request)).thenReturn(command);
        when(updateUserProfileOfficesHandler.handle(command, actorOid)).thenReturn(expected);

        ResponseEntity<CommandResult> response = controller.updateOffices(request, authenticatedUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(mapper).toUpdateUserProfileOfficesCommand(request);
        verify(updateUserProfileOfficesHandler).handle(command, actorOid);
        verifyNoInteractions(updateUserProfileUnrestrictedOfficesHandler);
    }

    @Test
    void updateOffices_dispatchesUnrestrictedAccessRequestToHandler() {
        UUID profileId = UUID.randomUUID();
        String actorOid = UUID.randomUUID().toString();
        UpdateOfficesUnrestrictedCommand request = new UpdateOfficesUnrestrictedCommand(profileId, true);
        UpdateUserProfileUnrestrictedOfficesCommand command =
                new UpdateUserProfileUnrestrictedOfficesCommand(profileId, true);
        CommandResult expected = CommandResult.success("unrestricted access updated");
        AuthenticatedEntraUser authenticatedUser = authenticatedUser(actorOid);
        when(mapper.toUpdateUserProfileUnrestrictedOfficesCommand(request)).thenReturn(command);
        when(updateUserProfileUnrestrictedOfficesHandler.handle(command, actorOid)).thenReturn(expected);

        ResponseEntity<CommandResult> response = controller.updateOffices(request, authenticatedUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(mapper).toUpdateUserProfileUnrestrictedOfficesCommand(request);
        verify(updateUserProfileUnrestrictedOfficesHandler).handle(command, actorOid);
        verifyNoInteractions(updateUserProfileOfficesHandler);
    }

    private static AuthenticatedEntraUser authenticatedUser(String actorOid) {
        return AuthenticatedEntraUser.builder().entraOid(actorOid).build();
    }

    @Nested
    class UserProfileCommandControllerRefreshStatusTest {

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
                    ACTOR, null,
                    List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
        }
    }
}
