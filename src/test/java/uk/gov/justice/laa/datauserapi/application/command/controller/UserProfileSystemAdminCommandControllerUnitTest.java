package uk.gov.justice.laa.datauserapi.application.command.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.command.handler.RefreshSilasStatusHandler;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.RefreshSilasStatusCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@ExtendWith(MockitoExtension.class)
class UserProfileSystemAdminCommandControllerUnitTest {

    @Mock
    private RefreshSilasStatusHandler refreshSilasStatusHandler;

    @InjectMocks
    private UserProfileSystemAdminCommandController controller;

    private final AuthenticatedEntraUser actor =
            AuthenticatedEntraUser.builder().entraOid("actor-oid").build();

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void refreshSilasStatus_returnsHandlerResult(boolean accepted) {
        CommandResult expected = new CommandResult(accepted, accepted ? "Accepted" : "Worker busy");
        when(refreshSilasStatusHandler.handle(new RefreshSilasStatusCommand(), actor)).thenReturn(expected);

        ResponseEntity<CommandResult> response = controller.refreshSilasStatus(actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(refreshSilasStatusHandler).handle(new RefreshSilasStatusCommand(), actor);
    }

    @Test
    void refreshSilasStatus_passesAuthenticatedActorToHandler() {
        when(refreshSilasStatusHandler.handle(new RefreshSilasStatusCommand(), actor))
                .thenReturn(CommandResult.success("ok"));

        controller.refreshSilasStatus(actor);

        verify(refreshSilasStatusHandler).handle(new RefreshSilasStatusCommand(), actor);
    }
}
