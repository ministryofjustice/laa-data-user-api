package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileUnrestrictedOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserProfileOfficesMapper;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateOfficesUnrestrictedCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesRequest;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.config.SecurityConfig;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebMvcTest(UserProfileCommandController.class)
@Import({SecurityConfig.class, UserProfileCommandController.class})
@ActiveProfiles("test")
class UserProfileCommandControllerTest {

    @MockitoBean
    private UpdateUserProfileOfficesHandler updateUserProfileOfficesHandler;

    @MockitoBean
    private UpdateUserProfileUnrestrictedOfficesHandler updateUserProfileUnrestrictedOfficesHandler;

    @MockitoBean
    private UpdateUserProfileOfficesMapper mapper;

    @MockitoBean
    private JwtToAuthenticatedEntraUserConverter converter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UserProfileCommandController controller;

    @BeforeEach
    void setUp() {
        controller = new UserProfileCommandController(
                updateUserProfileOfficesHandler, mapper, updateUserProfileUnrestrictedOfficesHandler);
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
        when(updateUserProfileOfficesHandler.handle(command, authenticatedUser)).thenReturn(expected);

        ResponseEntity<CommandResult> response = controller.updateOffices(request, authenticatedUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(mapper).toUpdateUserProfileOfficesCommand(request);
        verify(updateUserProfileOfficesHandler).handle(command, authenticatedUser);
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
        when(updateUserProfileUnrestrictedOfficesHandler.handle(command, authenticatedUser)).thenReturn(expected);

        ResponseEntity<CommandResult> response = controller.updateOffices(request, authenticatedUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(mapper).toUpdateUserProfileUnrestrictedOfficesCommand(request);
        verify(updateUserProfileUnrestrictedOfficesHandler).handle(command, authenticatedUser);
        verifyNoInteractions(updateUserProfileOfficesHandler);
    }

    private static AuthenticatedEntraUser authenticatedUser(String actorOid) {
        return AuthenticatedEntraUser.builder().entraOid(actorOid).build();
    }

}
