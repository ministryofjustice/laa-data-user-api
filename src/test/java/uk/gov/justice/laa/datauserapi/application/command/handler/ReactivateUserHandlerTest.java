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
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

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
class ReactivateUserHandlerTest {

    private final UUID targetUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final String actorIdStr = actorId.toString();
    @Mock
    private EntraUserCommandRepository entraUserCommandRepository;
    @Mock
    private UserAccountStatusAuditRepository auditRepository;
    @Mock
    private TechServicesClient techServicesClient;
    @Mock
    private ReactivateUserMapper mapper;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private UserCommandService userCommandService;
    private ReactivateUserHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReactivateUserHandler(entraUserCommandRepository, auditRepository, techServicesClient, mapper, modelMapper, userCommandService);
    }

    @Nested
    @DisplayName("Validation and Guard Clause Tests")
    class ValidationChecks {

        @Test
        @DisplayName("Should return failure when actor attempts self-reactivation")
        void handle_WhenActorIsSameAsTargetUser_ReturnsFailure() {
            // Distinct instances with same UUID value; verifies Objects.equals
            UUID sameUserId = UUID.fromString("33333333-3333-3333-3333-333333333333");
            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(sameUserId)
                    .entraOid(sameUserId.toString())
                    .email("actor@example.com")
                    .active(true)
                    .build();

            ReactivateUserCommand command = ReactivateUserCommand.builder().userEntraObjectId(sameUserId).comments("Reactivating account").build();

            CommandResult result = handler.handle(command, actorUser);

            assertThat(result).isNotNull();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("User can not reactive self");

            verifyNoInteractions(userCommandService, entraUserCommandRepository, techServicesClient, auditRepository);
        }

        @Test
        @DisplayName("Should return failure when target user is an internal user")
        void handle_WhenTargetUserIsInternal_ReturnsFailure() {
            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .email("actor@example.com")
                    .active(true)
                    .build();
            ReactivateUserCommand command = ReactivateUserCommand.builder().userEntraObjectId(targetUserId).comments("Reactivating internal staff").build();

            when(userCommandService.isInternalUser(targetUserId)).thenReturn(true);

            CommandResult result = handler.handle(command, actorUser);

            assertThat(result).isNotNull();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("User can not reactive internal user");

            verify(userCommandService).isInternalUser(targetUserId);
            verifyNoMoreInteractions(userCommandService);
            verifyNoInteractions(entraUserCommandRepository, techServicesClient, auditRepository);
        }
    }

    @Nested
    @DisplayName("Lookup and External Service Failure Tests")
    class LookupAndFailureTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user is not found in database")
        void handle_WhenTargetUserNotFound_ThrowsException() {
            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .email("actor@example.com")
                    .active(true)
                    .build();
            ReactivateUserCommand command = ReactivateUserCommand.builder().userEntraObjectId(targetUserId)
                    .comments("Reactivate missing user").build();

            when(userCommandService.isInternalUser(targetUserId)).thenReturn(false);
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(targetUserId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command, actorUser)).isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User account not found for oid: " + targetUserId);

            verifyNoInteractions(techServicesClient, auditRepository);
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
            ReactivateUserCommand command = ReactivateUserCommand.builder().userEntraObjectId(targetUserId).comments("Reactivating user").build();

            EntraUser realTargetUser = EntraUser.builder().id(targetUserId).email("john.doe@example.com").active(false).build();

            EntraUserDto userDto = EntraUserDto.builder().id(String.valueOf(targetUserId)).email("john.doe@example.com").build();

            when(userCommandService.isInternalUser(targetUserId)).thenReturn(false);
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(targetUserId))).thenReturn(Optional.of(realTargetUser));
            when(modelMapper.map(realTargetUser, EntraUserDto.class)).thenReturn(userDto);

            doThrow(new TechServicesClientException("Downstream TS error")).when(techServicesClient).reactivateUser(userDto);

            assertThatThrownBy(() -> handler.handle(command, actorUser)).isInstanceOf(TechServicesClientException.class)
                    .hasMessage("Downstream TS error");

            verify(entraUserCommandRepository, never()).save(any());
            verifyNoInteractions(auditRepository);
        }
    }

    @Nested
    @DisplayName("Successful Reactivation Tests")
    class SuccessExecutionTests {

        @Test
        @DisplayName("Should successfully reactivate user (firm scoping is enforced by ProviderAdminFirmScopePolicy)")
        void handle_WhenActorIsNonExternal_Succeeds() {
            executeSuccessFlow();
        }

        private void executeSuccessFlow() {
            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .email("actor@example.com")
                    .active(true)
                    .entraOid(actorId.toString())
                    .build();
            String testComments = "Account reactivation verified";
            final ReactivateUserCommand command = ReactivateUserCommand.builder().userEntraObjectId(targetUserId).comments(testComments).build();

            final EntraUser realTargetUser = EntraUser.builder().id(targetUserId).email("user@example.com")
                    .active(false).entraOid(targetUserId.toString()).build();

            final EntraUserDto userDto = EntraUserDto.builder().id(String.valueOf(targetUserId)).email("user@example.com").build();

            final UserAccountStatusAudit realAudit = UserAccountStatusAudit.builder().id(UUID.randomUUID()).entraUserId(realTargetUser.getId())
                    .statusChangedBy(actorIdStr).comments(testComments).build();

            final CommandResult expectedResult = CommandResult.success("User reactivated successfully");

            when(userCommandService.isInternalUser(targetUserId)).thenReturn(false);

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(targetUserId))).thenReturn(Optional.of(realTargetUser));
            when(modelMapper.map(realTargetUser, EntraUserDto.class)).thenReturn(userDto);
            when(mapper.toAuditEntity(realTargetUser, actorIdStr, testComments)).thenReturn(realAudit);
            when(mapper.toCommandResult(realTargetUser)).thenReturn(expectedResult);

            // Act
            CommandResult actualResult = handler.handle(command, actorUser);

            // Assert
            assertThat(actualResult).isNotNull();
            assertThat(actualResult.success()).isTrue();
            assertThat(actualResult.message()).isEqualTo("User reactivated successfully");

            // Verify actual entity persistence
            ArgumentCaptor<EntraUser> userCaptor = ArgumentCaptor.forClass(EntraUser.class);
            verify(entraUserCommandRepository).save(userCaptor.capture());
            EntraUser persistedUser = userCaptor.getValue();
            assertThat(persistedUser.getId()).isEqualTo(targetUserId);

            // Verify audit entity persistence
            verify(auditRepository).save(realAudit);

            // Verify strict ordering of operations
            InOrder inOrder = inOrder(techServicesClient, entraUserCommandRepository, mapper, auditRepository);

            inOrder.verify(techServicesClient).reactivateUser(userDto);
            inOrder.verify(entraUserCommandRepository).save(realTargetUser);
            inOrder.verify(mapper).toAuditEntity(realTargetUser, actorIdStr, testComments);
            inOrder.verify(auditRepository).save(realAudit);
            inOrder.verify(mapper).toCommandResult(realTargetUser);
        }
    }
}
