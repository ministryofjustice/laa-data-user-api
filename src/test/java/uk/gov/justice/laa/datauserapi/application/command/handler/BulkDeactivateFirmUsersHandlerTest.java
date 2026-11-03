package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
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
    private UserAccountStatusAuditRepository auditRepository;

    @Mock
    private DeactivateUserMapper deactivateUserMapper;

    @Mock
    private DeactivateUserHandler deactivateUserHandler;

    private BulkDeactivateFirmUsersHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BulkDeactivateFirmUsersHandler(
                firmCommandRepository, entraUserCommandRepository, auditRepository,
                deactivateUserMapper, deactivateUserHandler);
    }

    @Test
    void handle_deactivatesEachActiveUserLinkedToFirm() {
        String firmId = "123456";
        UUID firstUserEntraOid = UUID.randomUUID();
        UUID secondUserEntraOid = UUID.randomUUID();
        EntraUser firstUser = EntraUser.builder()
                .id(UUID.randomUUID())
                .entraOid(firstUserEntraOid.toString())
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .build();
        EntraUser secondUser = EntraUser.builder()
                .id(UUID.randomUUID())
                .entraOid(secondUserEntraOid.toString())
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .build();
        final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                .id(UUID.randomUUID())
                .email("actor@example.com")
                .active(true)
                .build();
        BulkDeactivateFirmUsersCommand command =
                new BulkDeactivateFirmUsersCommand(firmId, DeactivateUserReason.FirmClosureorMerger);

        when(firmCommandRepository.existsByCode(firmId)).thenReturn(true);
        when(entraUserCommandRepository.findUsersByFirmCode(firmId))
                .thenReturn(List.of(firstUser, secondUser));
        when(deactivateUserHandler.handle(any(DeactivateUserCommand.class), any(AuthenticatedEntraUser.class)))
                .thenReturn(CommandResult.success("done"), CommandResult.success("done"));

        CommandResult result = handler.handle(command, actorUser);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Deactivated 2 users");
        verify(entraUserCommandRepository).findUsersByFirmCode(firmId);
        ArgumentCaptor<DeactivateUserCommand> commandCaptor = ArgumentCaptor.forClass(DeactivateUserCommand.class);
        verify(deactivateUserHandler, times(2)).handle(commandCaptor.capture(), eq(actorUser));
        assertThat(commandCaptor.getAllValues())
                .extracting(DeactivateUserCommand::userEntraObjectId)
                .containsExactly(firstUserEntraOid, secondUserEntraOid);
        assertThat(commandCaptor.getAllValues())
                .allSatisfy(c -> assertThat(c.deactivateReason()).isEqualTo(DeactivateUserReason.FirmClosureorMerger));
    }

    @Test
    void handle_returnsFailureWhenAnyUserCannotBeDeactivated() {
        String firmId = "123456";
        UUID userEntraOid = UUID.randomUUID();
        EntraUser user = EntraUser.builder()
                .id(UUID.randomUUID())
                .entraOid(userEntraOid.toString())
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .build();
        final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                .id(UUID.randomUUID())
                .email("actor@example.com")
                .active(true)
                .build();
        BulkDeactivateFirmUsersCommand command =
                new BulkDeactivateFirmUsersCommand(firmId, DeactivateUserReason.FirmClosureorMerger);
        when(firmCommandRepository.existsByCode(firmId)).thenReturn(true);
        when(entraUserCommandRepository.findUsersByFirmCode(firmId))
                .thenReturn(List.of(user));
        when(deactivateUserHandler.handle(any(DeactivateUserCommand.class), any(AuthenticatedEntraUser.class)))
                .thenReturn(CommandResult.failure("not allowed"));

        CommandResult result = handler.handle(command, actorUser);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("failed to deactivate 1 users");
    }

    @Test
    void handle_auditsAlreadyDeactivatedUsersWithoutCallingDeactivateHandler() {
        String firmId = "123456";
        UUID userEntraOid = UUID.randomUUID();
        EntraUser alreadyDeactivatedUser = EntraUser.builder()
                .id(UUID.randomUUID())
                .entraOid(userEntraOid.toString())
                .userAccountStatus(UserAccountStatus.DEACTIVATED)
                .build();
        UserAccountStatusAudit audit = UserAccountStatusAudit.builder().build();
        AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                .id(UUID.randomUUID())
                .entraOid(UUID.randomUUID().toString())
                .email("actor@example.com")
                .active(true)
                .build();
        BulkDeactivateFirmUsersCommand command =
                new BulkDeactivateFirmUsersCommand(firmId, DeactivateUserReason.FirmClosureorMerger);

        when(firmCommandRepository.existsByCode(firmId)).thenReturn(true);
        when(entraUserCommandRepository.findUsersByFirmCode(firmId)).thenReturn(List.of(alreadyDeactivatedUser));
        when(deactivateUserMapper.toAuditEntity(
                alreadyDeactivatedUser, command.deactivateReason(), actorUser.entraOid())).thenReturn(audit);

        CommandResult result = handler.handle(command, actorUser);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("audited 1 already-deactivated users");
        verify(auditRepository).save(audit);
        verifyNoInteractions(deactivateUserHandler);
    }

    @Test
    void handle_throwsNotFoundWhenFirmDoesNotExist() {
        String firmId = "123456";
        when(firmCommandRepository.existsByCode(firmId)).thenReturn(false);

        final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                .id(UUID.randomUUID())
                .email("actor@example.com")
                .active(true)
                .build();

        assertThatThrownBy(() -> handler.handle(
                new BulkDeactivateFirmUsersCommand(firmId, DeactivateUserReason.FirmClosureorMerger), actorUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(firmId);
        verifyNoInteractions(entraUserCommandRepository, auditRepository, deactivateUserMapper, deactivateUserHandler);
    }
}
