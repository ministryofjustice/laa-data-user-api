package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.service.DeactivationTypeResolver;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateUserHandlerTest {

    @Mock
    private EntraUserCommandRepository entraUserCommandRepository;

    @Mock
    private UserAccountStatusAuditRepository auditRepository;

    @Mock
    private DeactivateUserMapper deactivateUserMapper;

    @Mock
    private DeactivationTypeResolver deactivationTypeResolver;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private TechServicesClient techServicesClient;

    @Mock
    private UserCommandService userCommandService;

    private DeactivateUserHandler handler;

    private final UUID targetUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final String actorIdStr = actorId.toString();

    @BeforeEach
    void setUp() {
        handler = new DeactivateUserHandler(
                entraUserCommandRepository,
                auditRepository,
                deactivateUserMapper,
                deactivationTypeResolver,
                modelMapper,
                techServicesClient,
                userCommandService
        );
    }

    @Nested
    @DisplayName("Pre-condition and Validation Checks")
    class ValidationChecks {

        @Test
        @DisplayName("Should fail when actor attempts self-deactivation")
        void handle_WhenActorIsTargetUser_ReturnsFailure() {
            // Reusing identical UUID instance to trigger handler's '==' identity check
            UUID sameUserId = UUID.fromString("33333333-3333-3333-3333-333333333333");
            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(sameUserId)
                    .entraOid(sameUserId.toString())
                    .email("actor@example.com")
                    .active(true)
                    .build();

            DeactivateUserCommand command = DeactivateUserCommand.builder()
                    .userEntraObjectId(sameUserId)
                    .deactivateReason(DeactivateUserReason.Absence)
                    .build();

            CommandResult result = handler.handle(command, actorUser);

            assertThat(result).isNotNull();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("User can not reactive self");

            verifyNoInteractions(userCommandService, entraUserCommandRepository, techServicesClient, auditRepository);
        }

        @Test
        @DisplayName("Should fail when target user is an internal user")
        void handle_WhenTargetUserIsInternal_ReturnsFailure() {
            DeactivateUserCommand command = DeactivateUserCommand.builder()
                    .userEntraObjectId(targetUserId)
                    .deactivateReason(DeactivateUserReason.Absence)
                    .build();
            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .email("actor@example.com")
                    .active(true)
                    .build();

            when(userCommandService.isInternalUser(targetUserId)).thenReturn(true);

            CommandResult result = handler.handle(command, actorUser);

            assertThat(result).isNotNull();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("User can not deactivate internal user");

            verify(userCommandService).isInternalUser(targetUserId);
            verifyNoMoreInteractions(userCommandService);
            verifyNoInteractions(entraUserCommandRepository, techServicesClient, auditRepository);
        }
    }

    @Nested
    @DisplayName("Resource Lookup & External Exceptions")
    class ResourceLookupAndFailures {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when target user is not found")
        void handle_WhenTargetUserNotFound_ThrowsException() {
            DeactivateUserCommand command = DeactivateUserCommand.builder()
                    .userEntraObjectId(targetUserId)
                    .deactivateReason(DeactivateUserReason.Absence)
                    .build();

            when(userCommandService.isInternalUser(targetUserId)).thenReturn(false);
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(targetUserId))).thenReturn(Optional.empty());

            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .email("actor@example.com")
                    .active(true)
                    .build();

            assertThatThrownBy(() -> handler.handle(command, actorUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User account not found for oid: " + targetUserId);

            verifyNoInteractions(techServicesClient, deactivationTypeResolver, auditRepository);
        }

        @Test
        @DisplayName("Should propagate TechServicesClientException when tech services client fails")
        void handle_WhenTechServicesClientFails_ThrowsException() {
            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .email("actor@example.com")
                    .active(true)
                    .build();
            DeactivateUserCommand command = DeactivateUserCommand.builder()
                    .userEntraObjectId(targetUserId)
                    .deactivateReason(DeactivateUserReason.NotActive)
                    .build();

            EntraUser realTargetUser = EntraUser.builder()
                    .id(targetUserId)
                    .email("target@example.com")
                    .active(true)
                    .build();

            EntraUserDto userDto = EntraUserDto.builder()
                    .id(String.valueOf(targetUserId))
                    .email("target@example.com")
                    .build();

            when(userCommandService.isInternalUser(targetUserId)).thenReturn(false);
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(targetUserId))).thenReturn(Optional.of(realTargetUser));
            when(modelMapper.map(realTargetUser, EntraUserDto.class)).thenReturn(userDto);

            doThrow(new TechServicesClientException("Downstream API timeout"))
                    .when(techServicesClient).deactivateUser(userDto, DeactivateUserReason.NotActive.name());

            assertThatThrownBy(() -> handler.handle(command, actorUser))
                    .isInstanceOf(TechServicesClientException.class)
                    .hasMessage("Downstream API timeout");

            // Verify target user state was not modified and not persisted
            verify(entraUserCommandRepository, never()).save(any());
            verifyNoInteractions(auditRepository);
        }
    }

    @Nested
    @DisplayName("Successful Processing")
    class SuccessfulExecution {

        @Test
        @DisplayName("Should deactivate user successfully (firm scoping is enforced by ProviderAdminFirmScopePolicy)")
        void handle_WhenActorIsNonExternal_ProcessesSuccessfully() {
            executeSuccessfulFlow();
        }

        private void executeSuccessfulFlow() {
            // Real target user entity
            EntraUser realTargetUser = EntraUser.builder()
                    .id(targetUserId)
                    .entraOid(targetUserId.toString())
                    .email("target@example.com")
                    .active(true)
                    .build();

            // Real actor user entity
            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .email("actor@example.com")
                    .active(true)
                    .build();

            final EntraUserDto userDto = EntraUserDto.builder()
                    .id(String.valueOf(targetUserId))
                    .email("target@example.com")
                    .build();

            final UserAccountStatusAudit realAudit = UserAccountStatusAudit.builder()
                    .id(UUID.randomUUID())
                    .entraUserId(realTargetUser.getId())
                    .statusChangedBy(actorIdStr)
                    .build();

            final CommandResult expectedResult = CommandResult.success("User deactivated successfully");

            when(userCommandService.isInternalUser(targetUserId)).thenReturn(false);

            final DeactivateUserCommand command = DeactivateUserCommand.builder()
                    .userEntraObjectId(targetUserId)
                    .deactivateReason(DeactivateUserReason.Absence)
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(targetUserId))).thenReturn(Optional.of(realTargetUser));
            when(modelMapper.map(realTargetUser, EntraUserDto.class)).thenReturn(userDto);
            when(deactivationTypeResolver.resolve(actorUser)).thenReturn(DeactivationType.LAA);
            when(deactivateUserMapper.toAuditEntity(realTargetUser, command.deactivateReason(), actorIdStr)).thenReturn(realAudit);
            when(deactivateUserMapper.toCommandResult(realTargetUser)).thenReturn(expectedResult);

            // Act
            CommandResult actualResult = handler.handle(command, actorUser);

            // Assert
            assertThat(actualResult).isNotNull();
            assertThat(actualResult.success()).isTrue();
            assertThat(actualResult.message()).isEqualTo("User deactivated successfully");

            // Verify actual entity state mutation via ArgumentCaptor
            ArgumentCaptor<EntraUser> userCaptor = ArgumentCaptor.forClass(EntraUser.class);
            verify(entraUserCommandRepository).save(userCaptor.capture());
            EntraUser savedUser = userCaptor.getValue();
            assertThat(savedUser.getId()).isEqualTo(targetUserId);

            // Verify audit persistence with actual instance
            verify(auditRepository).save(realAudit);

            // Verify strict order of execution
            InOrder inOrder = inOrder(
                    techServicesClient,
                    deactivationTypeResolver,
                    entraUserCommandRepository,
                    deactivateUserMapper,
                    auditRepository
            );

            inOrder.verify(techServicesClient).deactivateUser(userDto, DeactivateUserReason.Absence.name());
            inOrder.verify(deactivationTypeResolver).resolve(actorUser);
            inOrder.verify(entraUserCommandRepository).save(realTargetUser);
            inOrder.verify(deactivateUserMapper).toAuditEntity(realTargetUser, command.deactivateReason(), actorIdStr);
            inOrder.verify(auditRepository).save(realAudit);
            inOrder.verify(deactivateUserMapper).toCommandResult(realTargetUser);
        }
    }
}
