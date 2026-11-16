package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.datauserapi.application.command.service.UserProfileOfficeService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateUserProfileUnrestrictedOfficesHandlerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void delegatesAccessModeAndActorAndPreservesResult(boolean unrestricted) {
        UserProfileOfficeService service = mock(UserProfileOfficeService.class);
        UpdateUserProfileUnrestrictedOfficesCommand command =
                new UpdateUserProfileUnrestrictedOfficesCommand(UUID.randomUUID(), unrestricted);
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();
        CommandResult expected = CommandResult.success("updated");
        when(service.updateUnrestrictedAccess(command, actor)).thenReturn(expected);

        assertThat(new UpdateUserProfileUnrestrictedOfficesHandler(service).handle(command, actor)).isSameAs(expected);
        verify(service).updateUnrestrictedAccess(command, actor);
    }
}
