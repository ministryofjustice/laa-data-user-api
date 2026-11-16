package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.datauserapi.application.command.service.UserProfileOfficeService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateUserProfileOfficesHandlerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void delegatesCommandAndActorAndPreservesResult(boolean success) {
        UserProfileOfficeService service = mock(UserProfileOfficeService.class);
        UpdateUserProfileOfficesCommand command = new UpdateUserProfileOfficesCommand(UUID.randomUUID(), Set.of());
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();
        CommandResult expected = success ? CommandResult.success("updated") : CommandResult.failure("rejected");
        when(service.updateOffices(command, actor)).thenReturn(expected);

        assertThat(new UpdateUserProfileOfficesHandler(service).handle(command, actor)).isSameAs(expected);
        verify(service).updateOffices(command, actor);
    }
}
