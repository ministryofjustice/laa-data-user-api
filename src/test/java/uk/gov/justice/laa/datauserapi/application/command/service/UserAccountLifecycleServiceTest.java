package uk.gov.justice.laa.datauserapi.application.command.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyDeactivatedException;
import uk.gov.justice.laa.datauserapi.exception.UserNotDeactivatedException;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountLifecycleServiceTest {

    private static final String ACTOR = "22222222-2222-2222-2222-222222222222";

    private final UUID targetId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private EntraUserCommandRepository entraUserCommandRepository;
    @Mock
    private UserAccountStatusAuditRepository auditRepository;
    @Mock
    private DeactivateUserMapper deactivateUserMapper;
    @Mock
    private ReactivateUserMapper reactivateUserMapper;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private EntityManager entityManager;

    private UserAccountLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountLifecycleService(
                entraUserCommandRepository, auditRepository, deactivateUserMapper, reactivateUserMapper,
                modelMapper, entityManager);
    }

    private EntraUser user() {
        return EntraUser.builder().id(targetId).entraOid(targetId.toString()).email("t@example.com").active(true).build();
    }

    private void givenNoUser() {
        when(entraUserCommandRepository.findByEntraOid(targetId.toString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("loadEntraUserDtoByEntraOid maps the loaded user to a DTO")
    void loadEntraUserDtoByEntraOid_MapsUser() {
        EntraUser user = user();
        EntraUserDto dto = EntraUserDto.builder().id(targetId.toString()).build();
        when(entraUserCommandRepository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(user));
        when(modelMapper.map(user, EntraUserDto.class)).thenReturn(dto);

        assertThat(service.loadEntraUserDtoByEntraOid(targetId)).isSameAs(dto);
    }

    @Test
    @DisplayName("loadEntraUserDtoByEntraOid throws when the user does not exist")
    void loadEntraUserDtoByEntraOid_WhenMissing_Throws() {
        givenNoUser();

        assertThatThrownBy(() -> service.loadEntraUserDtoByEntraOid(targetId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with ID: " + targetId);
    }

    @Test
    @DisplayName("loads and locks an active user before deactivation")
    void loadEntraUserDtoForDeactivation_LocksActiveUser() {
        EntraUser user = user();
        user.setUserAccountStatus(UserAccountStatus.ACTIVE);
        EntraUserDto dto = EntraUserDto.builder().id(targetId.toString()).build();
        when(entraUserCommandRepository.findByEntraOidForUpdate(targetId.toString())).thenReturn(Optional.of(user));
        when(modelMapper.map(user, EntraUserDto.class)).thenReturn(dto);

        assertThat(service.loadEntraUserDtoForDeactivation(targetId)).isSameAs(dto);

        verify(entraUserCommandRepository).findByEntraOidForUpdate(targetId.toString());
        verify(entityManager).refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("refuses to call Tech Services when the locked user is already deactivated")
    void loadEntraUserDtoForDeactivation_AlreadyDeactivated_Throws() {
        EntraUser user = user();
        user.setUserAccountStatus(UserAccountStatus.DEACTIVATED);
        when(entraUserCommandRepository.findByEntraOidForUpdate(targetId.toString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadEntraUserDtoForDeactivation(targetId))
                .isInstanceOf(UserAlreadyDeactivatedException.class)
                .hasMessage("User is already deactivated: " + targetId);

        verifyNoInteractions(modelMapper);
    }

    @Test
    @DisplayName("locks and returns current status details for reactivation")
    void loadUserForReactivation_LocksAndReturnsCurrentState() {
        EntraUser user = user();
        user.deactivate(ACTOR, DeactivationType.FIRM);
        EntraUserDto dto = EntraUserDto.builder().id(targetId.toString()).build();
        when(entraUserCommandRepository.findByEntraOidForUpdate(targetId.toString())).thenReturn(Optional.of(user));
        when(modelMapper.map(user, EntraUserDto.class)).thenReturn(dto);

        UserAccountLifecycleService.LockedUserForReactivation target = service.loadUserForReactivation(targetId);

        assertThat(target.user()).isSameAs(dto);
        assertThat(target.deactivationType()).isEqualTo(DeactivationType.FIRM);
        verify(entraUserCommandRepository).findByEntraOidForUpdate(targetId.toString());
    }

    @Test
    @DisplayName("rejects reactivation when the locked user is no longer deactivated")
    void loadUserForReactivation_ActiveUser_Throws() {
        EntraUser user = user();
        user.setUserAccountStatus(UserAccountStatus.ACTIVE);
        when(entraUserCommandRepository.findByEntraOidForUpdate(targetId.toString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadUserForReactivation(targetId))
                .isInstanceOf(UserNotDeactivatedException.class)
                .hasMessage("User is not deactivated: " + targetId);

        verifyNoInteractions(modelMapper);
    }

    @Test
    @DisplayName("deactivate mutates the user, saves it, then audits and returns the mapped result")
    void deactivate_MutatesSavesAndAudits() {
        EntraUser user = user();
        UserAccountStatusAudit audit = UserAccountStatusAudit.builder().id(UUID.randomUUID()).build();
        CommandResult expected = CommandResult.success("deactivated");
        when(entraUserCommandRepository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(user));
        when(deactivateUserMapper.toAuditEntity(user, DeactivateUserReason.Absence, ACTOR)).thenReturn(audit);
        when(deactivateUserMapper.toCommandResult(user)).thenReturn(expected);

        CommandResult result = service.deactivate(targetId, ACTOR, DeactivationType.LAA, DeactivateUserReason.Absence);

        assertThat(result).isSameAs(expected);
        assertThat(user.getUserAccountStatus()).isEqualTo(UserAccountStatus.DEACTIVATED);
        assertThat(user.getDeactivationType()).isEqualTo(DeactivationType.LAA);
        InOrder inOrder = inOrder(entraUserCommandRepository, auditRepository);
        inOrder.verify(entraUserCommandRepository).save(user);
        inOrder.verify(auditRepository).save(audit);
    }

    @Test
    @DisplayName("deactivate writes nothing when the user does not exist")
    void deactivate_WhenMissing_WritesNothing() {
        givenNoUser();

        assertThatThrownBy(() -> service.deactivate(
                targetId, ACTOR, DeactivationType.LAA, DeactivateUserReason.Absence))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(entraUserCommandRepository, never()).save(any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    @DisplayName("reactivate mutates the user, saves it, then audits and returns the mapped result")
    void reactivate_MutatesSavesAndAudits() {
        EntraUser user = user();
        user.deactivate(ACTOR, DeactivationType.LAA);
        UserAccountStatusAudit audit = UserAccountStatusAudit.builder().id(UUID.randomUUID()).build();
        CommandResult expected = CommandResult.success("reactivated");
        when(entraUserCommandRepository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(user));
        when(reactivateUserMapper.toAuditEntity(user, ACTOR, "ok")).thenReturn(audit);
        when(reactivateUserMapper.toCommandResult(user)).thenReturn(expected);

        CommandResult result = service.reactivate(targetId, ACTOR, "ok");

        assertThat(result).isSameAs(expected);
        assertThat(user.getUserAccountStatus()).isNotEqualTo(UserAccountStatus.DEACTIVATED);
        InOrder inOrder = inOrder(entraUserCommandRepository, auditRepository);
        inOrder.verify(entraUserCommandRepository).save(user);
        inOrder.verify(auditRepository).save(audit);
    }

    @Test
    @DisplayName("reactivate writes nothing when the user does not exist")
    void reactivate_WhenMissing_WritesNothing() {
        givenNoUser();

        assertThatThrownBy(() -> service.reactivate(targetId, ACTOR, "ok"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(entraUserCommandRepository, never()).save(any());
        verifyNoInteractions(auditRepository);
    }
}
