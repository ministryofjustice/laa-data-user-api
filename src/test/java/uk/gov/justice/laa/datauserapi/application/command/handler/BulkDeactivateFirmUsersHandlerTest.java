package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.datauserapi.application.command.dto.BulkDeactivationResult;
import uk.gov.justice.laa.datauserapi.application.command.dto.BulkDeactivationResult.Status;
import uk.gov.justice.laa.datauserapi.application.command.dto.BulkDeactivationResult.UserOutcome;
import uk.gov.justice.laa.datauserapi.application.command.mapper.BulkDeactivateFirmUsersMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.BulkDeactivateFirmUsersService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulkDeactivateFirmUsersHandlerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void delegatesAndPreservesAggregateResponse(boolean hasFailure) {
        BulkDeactivateFirmUsersService service = mock(BulkDeactivateFirmUsersService.class);
        BulkDeactivateFirmUsersCommand command =
                new BulkDeactivateFirmUsersCommand("FIRM1", DeactivateUserReason.FirmClosureorMerger);
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();
        List<UserOutcome> outcomes = hasFailure
                ? List.of(new UserOutcome("one", Status.DEACTIVATED), new UserOutcome("two", Status.ACCESS_DENIED))
                : List.of(new UserOutcome("one", Status.DEACTIVATED));
        when(service.deactivate(command, actor)).thenReturn(new BulkDeactivationResult(outcomes));

        CommandResult result = new BulkDeactivateFirmUsersHandler(service, new BulkDeactivateFirmUsersMapper())
                .handle(command, actor);

        assertThat(result.success()).isEqualTo(!hasFailure);
        assertThat(result.message()).isEqualTo(hasFailure
                ? "Deactivated 1 users; failed to deactivate 1 users for firm 'FIRM1'"
                : "Deactivated 1 users for firm 'FIRM1'");
        verify(service).deactivate(command, actor);
    }
}
