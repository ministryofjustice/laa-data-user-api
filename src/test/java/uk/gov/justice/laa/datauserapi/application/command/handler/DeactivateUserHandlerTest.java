package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.service.DeactivateUserService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeactivateUserHandlerTest {

    @Test
    void delegatesCommandAndActorWithoutChangingResult() {
        DeactivateUserService service = mock(DeactivateUserService.class);
        DeactivateUserCommand command = new DeactivateUserCommand(UUID.randomUUID(), DeactivateUserReason.Absence);
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();
        CommandResult expected = CommandResult.success("deactivated");
        when(service.deactivate(command.userEntraObjectId(), command.deactivateReason(), actor)).thenReturn(expected);

        assertThat(new DeactivateUserHandler(service).handle(command, actor)).isSameAs(expected);
        verify(service).deactivate(command.userEntraObjectId(), command.deactivateReason(), actor);
    }
}
