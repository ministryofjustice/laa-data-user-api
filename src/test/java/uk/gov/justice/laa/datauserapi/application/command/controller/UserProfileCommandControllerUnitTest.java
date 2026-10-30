package uk.gov.justice.laa.datauserapi.application.command.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileUnrestrictedOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserProfileOfficesMapper;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesUnrestrictedRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@ExtendWith(MockitoExtension.class)
class UserProfileCommandControllerUnitTest {

    @Mock
    private UpdateUserProfileOfficesHandler officesHandler;

    @Mock
    private UpdateUserProfileUnrestrictedOfficesHandler unrestrictedOfficesHandler;

    @Mock
    private UpdateUserProfileOfficesMapper mapper;

    @InjectMocks
    private UserProfileCommandController controller;

    private final AuthenticatedEntraUser actor =
            AuthenticatedEntraUser.builder().entraOid("actor-oid").build();

    @Test
    void updateOffices_withOfficeIds_mapsAndDelegatesToOfficesHandler() {
        UUID profileId = UUID.randomUUID();
        UpdateUserProfileOfficesRequest request =
                new UpdateUserProfileOfficesRequest(profileId, Set.of(UUID.randomUUID()));
        UpdateUserProfileOfficesCommand command =
                new UpdateUserProfileOfficesCommand(profileId, request.officeIds());
        CommandResult expected = CommandResult.success("offices updated");
        when(mapper.toUpdateUserProfileOfficesCommand(request)).thenReturn(command);
        when(officesHandler.handle(command, actor)).thenReturn(expected);

        ResponseEntity<CommandResult> response = controller.updateOffices(request, actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verifyNoInteractions(unrestrictedOfficesHandler);
    }

    @Test
    void updateOffices_withOfficeIds_returnsHandlerFailureResult() {
        UUID profileId = UUID.randomUUID();
        UpdateUserProfileOfficesRequest request =
                new UpdateUserProfileOfficesRequest(profileId, Set.of(UUID.randomUUID()));
        UpdateUserProfileOfficesCommand command =
                new UpdateUserProfileOfficesCommand(profileId, request.officeIds());
        CommandResult failure = CommandResult.failure("office outside firm");
        when(mapper.toUpdateUserProfileOfficesCommand(request)).thenReturn(command);
        when(officesHandler.handle(command, actor)).thenReturn(failure);

        ResponseEntity<CommandResult> response = controller.updateOffices(request, actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(failure);
    }

    @Test
    void updateOffices_withUnrestrictedAccess_mapsAndDelegatesToUnrestrictedHandler() {
        UUID profileId = UUID.randomUUID();
        UpdateUserProfileOfficesUnrestrictedRequest request = new UpdateUserProfileOfficesUnrestrictedRequest(profileId, true);
        UpdateUserProfileUnrestrictedOfficesCommand command =
                new UpdateUserProfileUnrestrictedOfficesCommand(profileId, true);
        CommandResult expected = CommandResult.success("unrestricted access updated");
        when(mapper.toUpdateUserProfileUnrestrictedOfficesCommand(request)).thenReturn(command);
        when(unrestrictedOfficesHandler.handle(command, actor)).thenReturn(expected);

        ResponseEntity<CommandResult> response = controller.updateOffices(request, actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verifyNoInteractions(officesHandler);
    }
}
