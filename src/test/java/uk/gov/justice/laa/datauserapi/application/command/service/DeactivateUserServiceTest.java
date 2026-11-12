package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.service.DeactivationTypeResolver;

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
class DeactivateUserServiceTest {

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
    private DeactivationTypeResolver deactivationTypeResolver;
    @Mock
    private TechServicesClient techServicesClient;

    private DeactivateUserService handler;

    @BeforeEach
    void setUp() {
        UserAccountLifecycleService lifecycleService = new UserAccountLifecycleService(
                userRepository, auditRepository, deactivateMapper, reactivateMapper, modelMapper, entityManager);
        handler = new DeactivateUserService(lifecycleService, deactivationTypeResolver, techServicesClient);
    }

    private DeactivateUserCommand command(DeactivateUserReason reason) {
        return DeactivateUserCommand.builder().userEntraObjectId(targetUserId).deactivateReason(reason).build();
    }

    @Test
    @DisplayName("Should propagate UserNotFoundException and call nothing downstream when the user is missing")
    void handle_WhenTargetUserNotFound_ThrowsException() {
        when(userRepository.findByEntraOidForUpdate(targetUserId.toString()))
                .thenThrow(new UserNotFoundException(targetUserId));

        assertThatThrownBy(() -> handler.deactivate(targetUserId, DeactivateUserReason.Absence, actor))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with ID: " + targetUserId);

        verifyNoInteractions(techServicesClient, deactivationTypeResolver);
    }

    @Test
    @DisplayName("Should not change local state when the Tech Services call fails")
    void handle_WhenTechServicesClientFails_DoesNotDeactivateLocally() {
        EntraUserDto userDto = EntraUserDto.builder().id(targetUserId.toString()).build();
        EntraUser user = EntraUser.builder().id(targetUserId).active(true).build();
        when(userRepository.findByEntraOidForUpdate(targetUserId.toString())).thenReturn(Optional.of(user));
        when(modelMapper.map(user, EntraUserDto.class)).thenReturn(userDto);
        doThrow(new TechServicesClientException("Downstream API timeout"))
                .when(techServicesClient).deactivateUser(userDto, DeactivateUserReason.NotActive.name());

        assertThatThrownBy(() -> handler.deactivate(targetUserId, DeactivateUserReason.NotActive, actor))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessage("Downstream API timeout");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditRepository, deactivationTypeResolver);
        assertThat(user.isActive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Should call Tech Services, resolve the type from the actor, then deactivate locally")
    void handle_WhenSuccessful_DeactivatesAfterTechServices(boolean active) {
        DeactivateUserCommand command = command(DeactivateUserReason.Absence);
        EntraUserDto userDto = EntraUserDto.builder().id(targetUserId.toString()).active(active).build();
        CommandResult expected = CommandResult.success("User deactivated successfully");
        EntraUser user = EntraUser.builder().id(targetUserId).active(active).build();
        when(userRepository.findByEntraOidForUpdate(targetUserId.toString())).thenReturn(Optional.of(user));
        when(modelMapper.map(user, EntraUserDto.class)).thenReturn(userDto);
        when(deactivationTypeResolver.resolve(actor)).thenReturn(DeactivationType.LAA);
        when(deactivateMapper.toCommandResult(user)).thenReturn(expected);

        CommandResult result = handler.deactivate(command.userEntraObjectId(), command.deactivateReason(), actor);

        assertThat(result).isSameAs(expected);
        InOrder inOrder = inOrder(userRepository, entityManager, techServicesClient, deactivationTypeResolver);
        inOrder.verify(userRepository).findByEntraOidForUpdate(targetUserId.toString());
        inOrder.verify(entityManager).refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        inOrder.verify(techServicesClient).deactivateUser(userDto, DeactivateUserReason.Absence.name());
        inOrder.verify(deactivationTypeResolver).resolve(actor);
        inOrder.verify(userRepository).save(user);
        verify(userRepository).findByEntraOidForUpdate(targetUserId.toString());
        verify(entityManager).refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        verify(userRepository, never()).findByEntraOid(any());
    }
}
