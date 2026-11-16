package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.BeforeEach;
import uk.gov.justice.laa.datauserapi.application.command.handler.ReactivateUserHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import org.modelmapper.ModelMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactivateUserServiceTest {

    private final UUID targetUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
            .id(actorId)
            .entraOid(actorId.toString())
            .email("actor@example.com")
            .active(true)
            .build();

    @Mock
    private EntraUserCommandRepository userRepository;
    @Mock
    private UserAccountStatusAuditRepository auditRepository;
    @Mock
    private DeactivateUserMapper deactivateMapper;
    @Mock
    private ReactivateUserMapper reactivateMapper;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private EntityManager entityManager;
    @Mock
    private TechServicesClient techServicesClient;

    private ReactivateUserHandler handler;

    @BeforeEach
    void setUp() {
        UserAccountLifecycleService lifecycleService = new UserAccountLifecycleService(
                userRepository, auditRepository, deactivateMapper, reactivateMapper, modelMapper, entityManager);
        handler = new ReactivateUserHandler(new ReactivateUserService(lifecycleService, techServicesClient));
    }

    private EntraUser givenLockedUser(EntraUserDto dto, DeactivationType type) {
        EntraUser user = EntraUser.builder().id(targetUserId).active(false).deactivationType(type).build();
        when(userRepository.findByEntraOidForUpdate(targetUserId.toString())).thenReturn(Optional.of(user));
        when(modelMapper.map(user, EntraUserDto.class)).thenReturn(dto);
        return user;
    }

    @Test
    @DisplayName("Should propagate UserNotFoundException and call nothing downstream when the user is missing")
    void handle_WhenTargetUserNotFound_ThrowsException() {
        ReactivateUserCommand command = ReactivateUserCommand.builder()
                .userEntraObjectId(targetUserId).comments("missing").build();
        when(userRepository.findByEntraOidForUpdate(targetUserId.toString()))
                .thenThrow(new UserNotFoundException(targetUserId));

        assertThatThrownBy(() -> handler.handle(command, actor))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with ID: " + targetUserId);

        verifyNoInteractions(techServicesClient);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    @DisplayName("Should not change local state when the Tech Services call fails")
    void handle_WhenTechServicesClientFails_DoesNotReactivateLocally() {
        ReactivateUserCommand command = ReactivateUserCommand.builder()
                .userEntraObjectId(targetUserId).comments("Reactivating user").build();
        EntraUserDto userDto = EntraUserDto.builder().id(targetUserId.toString()).build();
        final EntraUser user = givenLockedUser(userDto, DeactivationType.NONE);
        doThrow(new TechServicesClientException("Downstream TS error"))
                .when(techServicesClient).reactivateUser(userDto);

        assertThatThrownBy(() -> handler.handle(command, actor))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessage("Downstream TS error");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditRepository, reactivateMapper);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should call Tech Services before reactivating locally and return the service result")
    void handle_WhenSuccessful_ReactivatesAfterTechServices() {
        String comments = "Account reactivation verified";
        ReactivateUserCommand command = ReactivateUserCommand.builder()
                .userEntraObjectId(targetUserId).comments(comments).build();
        EntraUserDto userDto = EntraUserDto.builder().id(targetUserId.toString()).build();
        CommandResult expected = CommandResult.success("User reactivated successfully");
        EntraUser user = givenLockedUser(userDto, DeactivationType.NONE);
        when(reactivateMapper.toCommandResult(user)).thenReturn(expected);

        CommandResult result = handler.handle(command, actor);

        assertThat(result).isSameAs(expected);
        InOrder inOrder = inOrder(userRepository, entityManager, techServicesClient);
        inOrder.verify(userRepository).findByEntraOidForUpdate(targetUserId.toString());
        inOrder.verify(entityManager).refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        inOrder.verify(techServicesClient).reactivateUser(userDto);
        inOrder.verify(userRepository).save(user);
        verify(userRepository).findByEntraOidForUpdate(targetUserId.toString());
        verify(entityManager).refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        verify(userRepository, never()).findByEntraOid(any());
    }

    @Test
    @DisplayName("Rechecks hierarchy using the locked target before calling Tech Services")
    void handle_WhenLockedTargetHasPrivilegedDeactivation_DeniesBeforeTechServices() {
        ReactivateUserCommand command = ReactivateUserCommand.builder()
                .userEntraObjectId(targetUserId).comments("Reactivating user").build();
        EntraUserDto userDto = EntraUserDto.builder().id(targetUserId.toString()).build();
        final EntraUser user = givenLockedUser(userDto, DeactivationType.PRIVILEGED);

        assertThatThrownBy(() -> handler.handle(command, actor))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("PRIVILEGED");

        verifyNoInteractions(techServicesClient);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditRepository, reactivateMapper);
        assertThat(user.isActive()).isFalse();
    }
}
