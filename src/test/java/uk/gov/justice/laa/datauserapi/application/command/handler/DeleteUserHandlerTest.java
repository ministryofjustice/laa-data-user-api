package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.service.DeleteUserService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteUserHandlerTest {

    @Mock
    private DeleteUserService deleteUserService;

    @Test
    void delegatesDeletionToService() {
        DeleteUserHandler handler = new DeleteUserHandler(deleteUserService);
        DeleteUserCommand command = new DeleteUserCommand(UUID.randomUUID(), DeleteUserReason.LeftFirm);
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .build();
        CommandResult expected = CommandResult.success("deleted");
        when(deleteUserService.delete(command, actor)).thenReturn(expected);

        assertThat(handler.handle(command, actor)).isSameAs(expected);
        verify(deleteUserService).delete(command, actor);
    }
}
