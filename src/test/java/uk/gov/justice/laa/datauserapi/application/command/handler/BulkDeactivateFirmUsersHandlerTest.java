package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkDeactivateFirmUsersHandlerTest {

    @Mock
    private FirmCommandRepository firmCommandRepository;

    @Mock
    private EntraUserCommandRepository entraUserCommandRepository;

    @Mock
    private DeactivateUserHandler deactivateUserHandler;

    private BulkDeactivateFirmUsersHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BulkDeactivateFirmUsersHandler(
                firmCommandRepository, entraUserCommandRepository, deactivateUserHandler);
    }

    @Test
    void handle_deactivatesEachActiveSingleFirmUser() {
        UUID firmId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        String actorId = UUID.randomUUID().toString();
        BulkDeactivateFirmUsersCommand command =
                new BulkDeactivateFirmUsersCommand(firmId, DeactivateUserReason.FirmClosureorMerger);

        when(firmCommandRepository.existsById(firmId)).thenReturn(true);
        when(entraUserCommandRepository.findActiveSingleFirmUserIdsByFirmId(firmId, UserAccountStatus.ACTIVE))
                .thenReturn(List.of(firstUserId, secondUserId));
        when(deactivateUserHandler.handle(any(DeactivateUserCommand.class), eq(actorId)))
                .thenReturn(CommandResult.success("done"), CommandResult.success("done"));

        CommandResult result = handler.handle(command, actorId);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Deactivated 2 users");
        verify(entraUserCommandRepository).findActiveSingleFirmUserIdsByFirmId(firmId, UserAccountStatus.ACTIVE);
        ArgumentCaptor<DeactivateUserCommand> commandCaptor = ArgumentCaptor.forClass(DeactivateUserCommand.class);
        verify(deactivateUserHandler, times(2)).handle(commandCaptor.capture(), eq(actorId));
        assertThat(commandCaptor.getAllValues())
                .extracting(DeactivateUserCommand::userEntraObjectId)
                .containsExactly(firstUserId, secondUserId);
        assertThat(commandCaptor.getAllValues())
                .allSatisfy(c -> assertThat(c.deactivateReason()).isEqualTo(DeactivateUserReason.FirmClosureorMerger));
    }

    @Test
    void handle_returnsFailureWhenAnyUserCannotBeDeactivated() {
        UUID firmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BulkDeactivateFirmUsersCommand command =
                new BulkDeactivateFirmUsersCommand(firmId, DeactivateUserReason.FirmClosureorMerger);
        when(firmCommandRepository.existsById(firmId)).thenReturn(true);
        when(entraUserCommandRepository.findActiveSingleFirmUserIdsByFirmId(firmId, UserAccountStatus.ACTIVE))
                .thenReturn(List.of(userId));
        when(deactivateUserHandler.handle(any(DeactivateUserCommand.class), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CommandResult.failure("not allowed"));

        CommandResult result = handler.handle(command, UUID.randomUUID().toString());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("failed to deactivate 1 users");
    }

    @Test
    void handle_throwsNotFoundWhenFirmDoesNotExist() {
        UUID firmId = UUID.randomUUID();
        when(firmCommandRepository.existsById(firmId)).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(
                new BulkDeactivateFirmUsersCommand(firmId, DeactivateUserReason.FirmClosureorMerger), "actor"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(firmId.toString());
        verifyNoInteractions(entraUserCommandRepository, deactivateUserHandler);
    }
}
