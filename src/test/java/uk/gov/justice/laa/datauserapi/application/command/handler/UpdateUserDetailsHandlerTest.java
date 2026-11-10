package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.datauserapi.application.command.service.UpdateUserDetailsService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateUserDetailsHandlerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void delegatesCommandAndActorAndPreservesResult(boolean success) {
        UpdateUserDetailsService service = mock(UpdateUserDetailsService.class);
        UpdateUserDetailsCommand command = new UpdateUserDetailsCommand(UUID.randomUUID(), "New", null, null);
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();
        CommandResult expected = success ? CommandResult.success("updated") : CommandResult.failure("rejected");
        when(service.update(command, actor)).thenReturn(expected);

        assertThat(new UpdateUserDetailsHandler(service).handle(command, actor)).isSameAs(expected);
        verify(service).update(command, actor);
    }
}
