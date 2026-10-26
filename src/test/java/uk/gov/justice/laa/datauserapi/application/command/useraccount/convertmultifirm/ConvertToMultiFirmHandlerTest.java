package uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ConflictException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConvertToMultiFirmHandlerTest {

    @Mock
    private EntraUserCommandRepository repository;

    private ConvertToMultiFirmHandler handler;

    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final ConvertToMultiFirmCommand command = new ConvertToMultiFirmCommand(targetId);

    @BeforeEach
    void setUp() {
        handler = new ConvertToMultiFirmHandler(repository, new ConvertToMultiFirmDomainMapper());
    }

    @Test
    void handle_setsMultiFirmFlagAndSaves() {
        EntraUser target = EntraUser.builder().entraOid(targetId.toString()).multiFirmUser(false).build();
        when(repository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(target));

        CommandResult result = handler.handle(command,
                actor(UserType.INTERNAL, true, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM));

        assertThat(result.success()).isTrue();
        assertThat(target.isMultiFirmUser()).isTrue();
        assertThat(target.getLastModifiedBy()).isEqualTo(actorId.toString());
        verify(repository).save(target);
    }

    @Test
    void handle_whenAlreadyMultiFirm_throwsConflictAndDoesNotSave() {
        EntraUser target = EntraUser.builder().entraOid(targetId.toString()).multiFirmUser(true).build();
        when(repository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> handler.handle(command,
                actor(UserType.INTERNAL, true, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM)))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void handle_whenTargetMissing_throwsNotFound() {
        when(repository.findByEntraOid(targetId.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(command,
                actor(UserType.INTERNAL, true, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void handle_whenActorExternal_throwsAccessDenied() {
        assertThatThrownBy(() -> handler.handle(command,
                actor(UserType.EXTERNAL, true, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void handle_whenMissingConvertPermission_throwsAccessDenied() {
        assertThatThrownBy(() -> handler.handle(command, actor(UserType.INTERNAL, true, Permission.EDIT_EXTERNAL_USER)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void handle_whenMissingEditExternalPermission_throwsAccessDenied() {
        assertThatThrownBy(() -> handler.handle(command,
                actor(UserType.INTERNAL, true, Permission.CONVERT_USER_TO_MULTI_FIRM)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void handle_whenNoActiveProfile_throwsAccessDenied() {
        AuthenticatedEntraUser noProfile = AuthenticatedEntraUser.builder().entraOid(actorId.toString()).build();

        assertThatThrownBy(() -> handler.handle(command, noProfile)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void handle_whenProfileNotActive_throwsAccessDenied() {
        assertThatThrownBy(() -> handler.handle(command,
                actor(UserType.INTERNAL, false, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void handle_permissionsMaySpanMultipleRoles() {
        EntraUser target = EntraUser.builder().entraOid(targetId.toString()).multiFirmUser(false).build();
        when(repository.findByEntraOid(targetId.toString())).thenReturn(Optional.of(target));
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .appRoles(Set.of(role(Permission.EDIT_EXTERNAL_USER), role(Permission.CONVERT_USER_TO_MULTI_FIRM)))
                .build();

        CommandResult result = handler.handle(command, AuthenticatedEntraUser.builder()
                .entraOid(actorId.toString()).activeProfile(profile).build());

        assertThat(result.success()).isTrue();
    }

    private AuthenticatedEntraUser actor(UserType type, boolean active, Permission... permissions) {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .activeProfile(active)
                .userType(type)
                .appRoles(Set.of(role(permissions)))
                .build();
        return AuthenticatedEntraUser.builder().id(actorId).entraOid(actorId.toString()).activeProfile(profile).build();
    }

    private AuthenticatedAppRole role(Permission... permissions) {
        return new AuthenticatedAppRole(UUID.randomUUID(), "role", 0, "ROLE", false, null, null, null, false, null,
                Set.of(permissions));
    }
}
