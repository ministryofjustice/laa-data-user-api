package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.service.ReactivateUserService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReactivateUserHandlerTest {

    @Test
    void delegatesCommandAndActorAndPreservesResult() {
        ReactivateUserService service = mock(ReactivateUserService.class);
        ReactivateUserCommand command = new ReactivateUserCommand(UUID.randomUUID(), "verified");
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();
        CommandResult expected = CommandResult.success("reactivated");
        when(service.reactivate(command, actor)).thenReturn(expected);

        assertThat(new ReactivateUserHandler(service).handle(command, actor)).isSameAs(expected);
        verify(service).reactivate(command, actor);
    }
}
