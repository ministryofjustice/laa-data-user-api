package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import uk.gov.justice.laa.datauserapi.application.command.mapper.BulkDeactivateFirmUsersMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.BulkDeactivateFirmUsersService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.FirmNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class BulkDeactivateFirmUsersHandlerTest {

    private final BulkDeactivateFirmUsersService service = mock(BulkDeactivateFirmUsersService.class);
    private final BulkDeactivateFirmUsersHandler handler =
            new BulkDeactivateFirmUsersHandler(service, new BulkDeactivateFirmUsersMapper());
    private final BulkDeactivateFirmUsersCommand command =
            new BulkDeactivateFirmUsersCommand("FIRM1", DeactivateUserReason.FirmClosureorMerger);
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();

    @Test
    void validatesFirmThenStartsAsyncDeactivationAndReturnsImmediately() {
        CommandResult result = handler.handle(command, actor);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Bulk deactivation started");
        InOrder order = inOrder(service);
        order.verify(service).requireFirmExists("FIRM1");
        order.verify(service).deactivateAsync(command, actor);
    }

    @Test
    void unknownFirmIsRejectedWithoutStartingAsyncWork() {
        doThrow(new FirmNotFoundException("FIRM1")).when(service).requireFirmExists("FIRM1");

        assertThatThrownBy(() -> handler.handle(command, actor)).isInstanceOf(FirmNotFoundException.class);

        verify(service).requireFirmExists("FIRM1");
        verifyNoMoreInteractions(service);
    }
}
