package uk.gov.justice.laa.datauserapi.application.command.handler;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.RoleChangeNotificationService;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.DeleteUserReasonRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.DeleteUserReasonEntity;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.ReferenceDataNotFoundException;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteUserHandlerTest {

    @Mock
    private EntraUserCommandRepository entraUserCommandRepository;
    @Mock
    private UserProfileCommandRepository userProfileCommandRepository;
    @Mock
    private UserAccountStatusAuditRepository auditRepository;
    @Mock
    private DeleteUserReasonRepository deleteUserReasonRepository;
    @Mock
    private DeleteUserMapper deleteUserMapper;
    @Mock
    private UserCommandService userCommandService;
    @Mock
    private RoleChangeNotificationService roleChangeNotificationService;
    @Mock
    private TechServicesClient techServicesClient;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private EntityManager entityManager;

    private DeleteUserHandler handler;

    private final UUID targetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final DeleteUserCommand command = new DeleteUserCommand(targetId, DeleteUserReason.LeftFirm);

    @BeforeEach
    void setUp() {
        modelMapper = new ModelMapper();
        handler = new DeleteUserHandler(entraUserCommandRepository, userProfileCommandRepository,
                auditRepository, deleteUserReasonRepository, deleteUserMapper, userCommandService,
                roleChangeNotificationService, techServicesClient, modelMapper, entityManager);
    }

    @Test
    void rejectsSelfDeletion() {
        final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                .id(targetId)
                .entraOid(targetId.toString())
                .email("actor@example.com")
                .active(true)
                .build();
        CommandResult result = handler.handle(command, actorUser);

        assertThat(result.success()).isFalse();
        verifyNoInteractions(entraUserCommandRepository, userProfileCommandRepository, auditRepository);
    }

    @Test
    void throwsWhenUserNotFound() {
        final AuthenticatedEntraUser actorUser = internalActor();
        when(entraUserCommandRepository.findByEntraOid(targetId.toString())).thenReturn(Optional.empty());
        when(deleteUserReasonRepository.findByCode(DeleteUserReason.LeftFirm.name())).thenReturn(Optional.of(DeleteUserReasonEntity.builder().build()));

        assertThatThrownBy(() -> handler.handle(command, actorUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsInternalUser() {
        final AuthenticatedEntraUser actorUser = internalActor();
        when(entraUserCommandRepository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(EntraUser.builder().build()));
        when(userCommandService.isInternalUser(targetId)).thenReturn(true);
        when(deleteUserReasonRepository.findByCode(DeleteUserReason.LeftFirm.name())).thenReturn(Optional.of(DeleteUserReasonEntity.builder().build()));

        CommandResult result = handler.handle(command, actorUser);

        assertThat(result.success()).isFalse();
        verifyNoInteractions(userProfileCommandRepository, auditRepository);
    }

    @Test
    void externalActorCannotDeleteUserFromDifferentFirm() {
        final AuthenticatedEntraUser actorUser = externalActor(UUID.randomUUID());
        when(entraUserCommandRepository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(EntraUser.builder().build()));
        when(userCommandService.isInternalUser(targetId)).thenReturn(false);
        when(userCommandService.getActiveUserProfile(targetId)).thenReturn(profileInFirm(UUID.randomUUID()));
        when(deleteUserReasonRepository.findByCode(DeleteUserReason.LeftFirm.name()))
                .thenReturn(Optional.of(DeleteUserReasonEntity.builder().build()));

        CommandResult result = handler.handle(command, actorUser);

        assertThat(result.success()).isFalse();
        verifyNoInteractions(userProfileCommandRepository, auditRepository);
    }

    @Test
    void externalActorCannotDeleteMultiFirmUser() {
        final AuthenticatedEntraUser actorUser = externalActor(UUID.randomUUID());
        when(entraUserCommandRepository.findByEntraOid(targetId.toString()))
                .thenReturn(Optional.of(EntraUser.builder().multiFirmUser(true).build()));
        when(userCommandService.isInternalUser(targetId)).thenReturn(false);
        when(deleteUserReasonRepository.findByCode(DeleteUserReason.LeftFirm.name())).thenReturn(Optional.of(DeleteUserReasonEntity.builder().build()));

        CommandResult result = handler.handle(command, actorUser);

        assertThat(result.success()).isFalse();
        verifyNoInteractions(userProfileCommandRepository, auditRepository);
    }

    @Test
    void deletesProfilesAndUserAndRecordsAudit() {
        UserProfile profile = UserProfile.builder().build();
        EntraUser user = EntraUser.builder().entraOid(targetId.toString()).userProfiles(Set.of(profile)).build();
        UserAccountStatusAudit audit = UserAccountStatusAudit.builder().build();
        CommandResult expected = CommandResult.success("deleted");
        when(entraUserCommandRepository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(user));
        when(userCommandService.isInternalUser(targetId)).thenReturn(false);
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of(profile));
        when(deleteUserMapper.toAuditEntity(user, command, actorId.toString())).thenReturn(audit);
        when(deleteUserMapper.toCommandResult(user)).thenReturn(expected);
        when(deleteUserReasonRepository.findByCode(DeleteUserReason.LeftFirm.name())).thenReturn(Optional.of(DeleteUserReasonEntity.builder().build()));
        when(techServicesClient.deactivateUser(any(EntraUserDto.class), anyString()))
                .thenReturn(TechServicesApiResponse.success(ChangeAccountReactivationResponse.builder().build()));

        final AuthenticatedEntraUser actorUser = internalActor();

        CommandResult result = handler.handle(command, actorUser);

        assertThat(result).isSameAs(expected);
        InOrder order = inOrder(auditRepository, userProfileCommandRepository, entraUserCommandRepository);
        order.verify(auditRepository).save(audit);
        order.verify(userProfileCommandRepository).deleteAll(List.of(profile));
        order.verify(entraUserCommandRepository).delete(user);
    }

    @Test
    void throwsWhenDeleteReasonUnknown() {
        when(deleteUserReasonRepository.findByCode(DeleteUserReason.LeftFirm.name())).thenReturn(Optional.empty());

        final AuthenticatedEntraUser actorUser = internalActor();

        assertThatThrownBy(() -> handler.handle(command, actorUser))
                .isInstanceOf(ReferenceDataNotFoundException.class)
                .hasMessageContaining("Unknown delete reason code");
        verifyNoInteractions(entraUserCommandRepository, userProfileCommandRepository, auditRepository);
    }

    @Test
    void clearsAssociationsNotifiesPuiRolesAndDeletesWhenTechServicesFails() {
        final AuthenticatedEntraUser actorUser = internalActor();
        App app = App.builder().entraOid("app-oid").build();
        AppRole puiRole = AppRole.builder().ccmsCode("PUI_1").legacySync(true).app(app).build();
        AppRole otherRole = AppRole.builder().ccmsCode("OTHER").legacySync(false).app(app).build();
        UserProfile profile = UserProfile.builder()
                .appRoles(new HashSet<>(Set.of(puiRole, otherRole)))
                .offices(new HashSet<>(Set.of(Office.builder().build())))
                .build();
        EntraUser user = EntraUser.builder().entraOid(targetId.toString())
                .userProfiles(new HashSet<>(Set.of(profile))).build();
        stubUser(user);
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of(profile));
        when(techServicesClient.deactivateUser(any(), anyString()))
                .thenReturn(TechServicesApiResponse.error(null));
        doThrow(new RuntimeException("boom")).when(techServicesClient).deleteRoleAssignment(any());
        UserAccountStatusAudit audit = UserAccountStatusAudit.builder().build();
        CommandResult expected = CommandResult.success("deleted");
        when(deleteUserMapper.toAuditEntity(user, command, actorId.toString())).thenReturn(audit);
        when(deleteUserMapper.toCommandResult(user)).thenReturn(expected);

        CommandResult result = handler.handle(command, actorUser);

        assertThat(result).isSameAs(expected);
        assertThat(profile.getAppRoles()).isEmpty();
        assertThat(profile.getOffices()).isEmpty();
        assertThat(user.getUserProfiles()).isEmpty();
        verify(roleChangeNotificationService).sendMessage(profile, "app-oid", Collections.emptySet(), Set.of("PUI_1"));
        InOrder order = inOrder(userProfileCommandRepository, auditRepository, entraUserCommandRepository);
        order.verify(auditRepository).save(audit);
        order.verify(userProfileCommandRepository).saveAll(List.of(profile));
        order.verify(userProfileCommandRepository).deleteAll(List.of(profile));
        order.verify(entraUserCommandRepository).delete(user);
        order.verify(entraUserCommandRepository).flush();
    }

    @Test
    void skipsNotificationWhenNoPuiRoles() {
        AppRole role = AppRole.builder().legacySync(false).app(App.builder().entraOid("o").build()).build();
        UserProfile profile = UserProfile.builder().appRoles(new HashSet<>(Set.of(role))).build();
        EntraUser user = EntraUser.builder().entraOid(targetId.toString()).build();
        stubUser(user);
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of(profile));
        when(deleteUserMapper.toAuditEntity(any(), any(), anyString())).thenReturn(UserAccountStatusAudit.builder().build());

        final AuthenticatedEntraUser actorUser = internalActor();

        handler.handle(command, actorUser);

        verifyNoInteractions(roleChangeNotificationService);
        verify(entraUserCommandRepository).delete(user);
    }

    @Test
    void deletesUserWithNoProfiles() {
        EntraUser user = EntraUser.builder().entraOid(targetId.toString()).build();
        stubUser(user);
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of());
        when(deleteUserMapper.toAuditEntity(any(), any(), anyString())).thenReturn(UserAccountStatusAudit.builder().build());

        final AuthenticatedEntraUser actorUser = internalActor();

        handler.handle(command, actorUser);

        verify(userProfileCommandRepository, never()).deleteAll(any());
        verify(auditRepository).save(any());
        verify(entraUserCommandRepository).delete(user);
    }

    @Test
    void externalActorCanDeleteUserInSameFirm() {
        UUID firmId = UUID.randomUUID();
        EntraUser user = EntraUser.builder().entraOid(targetId.toString()).build();
        stubUser(user);
        when(userCommandService.getActiveUserProfile(targetId)).thenReturn(profileInFirm(firmId));
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of());
        when(deleteUserMapper.toAuditEntity(any(), any(), anyString())).thenReturn(UserAccountStatusAudit.builder().build());

        final AuthenticatedEntraUser actorUser = externalActor(firmId);

        handler.handle(command, actorUser);

        verify(entraUserCommandRepository).delete(user);
    }

    @Test
    void handlesNullProfilesAndNullAssociations() {
        UserProfile profile = UserProfile.builder().build();
        EntraUser user = EntraUser.builder().entraOid(targetId.toString()).build();
        stubUser(user);
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of(profile));
        when(deleteUserMapper.toAuditEntity(any(), any(), anyString())).thenReturn(UserAccountStatusAudit.builder().build());

        final AuthenticatedEntraUser actorUser = internalActor();

        handler.handle(command, actorUser);

        verifyNoInteractions(roleChangeNotificationService);
        verify(userProfileCommandRepository).deleteAll(List.of(profile));
    }

    @Test
    void handlesNullProfileListAndEmptyRoles() {
        UserProfile emptyRoles = UserProfile.builder().appRoles(new HashSet<>()).build();
        EntraUser user = EntraUser.builder().entraOid(targetId.toString()).build();
        stubUser(user);
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(null, List.of(emptyRoles));
        when(deleteUserMapper.toAuditEntity(any(), any(), anyString())).thenReturn(UserAccountStatusAudit.builder().build());

        final AuthenticatedEntraUser actorUser = internalActor();

        handler.handle(command, actorUser);
        handler.handle(command, actorUser);

        verify(userProfileCommandRepository).deleteAll(List.of(emptyRoles));
        verifyNoInteractions(roleChangeNotificationService);
    }

    @Test
    void ignoresLegacyRolesWhoseAppHasNoEntraOid() {
        AppRole role = AppRole.builder().ccmsCode("R").legacySync(true).app(App.builder().build()).build();
        UserProfile profile = UserProfile.builder().appRoles(new HashSet<>(Set.of(role))).build();
        EntraUser user = EntraUser.builder().entraOid(targetId.toString()).build();
        stubUser(user);
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of(profile));
        when(deleteUserMapper.toAuditEntity(any(), any(), anyString())).thenReturn(UserAccountStatusAudit.builder().build());

        final AuthenticatedEntraUser actorUser = internalActor();

        handler.handle(command, actorUser);

        verifyNoInteractions(roleChangeNotificationService);
        assertThat(profile.getAppRoles()).isEmpty();
    }

    private void stubUser(EntraUser user) {
        when(deleteUserReasonRepository.findByCode(DeleteUserReason.LeftFirm.name()))
                .thenReturn(Optional.of(DeleteUserReasonEntity.builder().code("LeftFirm").build()));
        when(entraUserCommandRepository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(user));
        when(userCommandService.isInternalUser(targetId)).thenReturn(false);
        lenient().when(techServicesClient.deactivateUser(any(), anyString()))
                .thenReturn(TechServicesApiResponse.success(ChangeAccountReactivationResponse.builder().build()));
    }

    private AuthenticatedEntraUser internalActor() {
        return actor(UserType.INTERNAL, null);
    }

    private AuthenticatedEntraUser externalActor(UUID firmId) {
        return actor(UserType.EXTERNAL, firmId);
    }

    private AuthenticatedEntraUser actor(UserType userType, UUID firmId) {
        return AuthenticatedEntraUser.builder()
                .id(actorId)
                .entraOid(actorId.toString())
                .email("actor@example.com")
                .active(true)
                .activeProfile(AuthenticatedUserProfile.builder().userType(userType).firmId(firmId).build())
                .build();
    }

    private UserProfile profileInFirm(UUID firmId) {
        return UserProfile.builder().firm(Firm.builder().id(firmId).build()).build();
    }
}
