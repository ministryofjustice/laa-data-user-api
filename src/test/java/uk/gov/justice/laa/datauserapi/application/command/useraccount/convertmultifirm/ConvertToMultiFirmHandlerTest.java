package uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.handler.ConvertToMultiFirmHandler;
import uk.gov.justice.laa.datauserapi.application.command.service.ConvertToMultiFirmService;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ConvertToMultiFirmDomainMapper;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ConvertToMultiFirmHandlerTest {

    @Mock
    private EntraUserCommandRepository repository;
    @Mock
    private EntityManager entityManager;

    private ConvertToMultiFirmHandler handler;

    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final ConvertToMultiFirmCommand command = new ConvertToMultiFirmCommand(targetId);

    @BeforeEach
    void setUp() {
        handler = new ConvertToMultiFirmHandler(
                new ConvertToMultiFirmService(repository, new ConvertToMultiFirmDomainMapper(), entityManager));
    }

    @Test
    void handle_delegatesToConversionService() {
        ConvertToMultiFirmService service = mock(ConvertToMultiFirmService.class);
        AuthenticatedEntraUser actor = actor(
                UserType.INTERNAL, true, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM);
        CommandResult expected = CommandResult.success("converted");
        when(service.convert(command, actor)).thenReturn(expected);

        assertThat(new ConvertToMultiFirmHandler(service).handle(command, actor)).isSameAs(expected);
        verify(service).convert(command, actor);
    }

    @Test
    void handle_setsMultiFirmFlagAndSaves() {
        EntraUser target = EntraUser.builder().entraOid(targetId.toString()).multiFirmUser(false).build();
        when(repository.findByEntraOidForUpdate(targetId.toString())).thenReturn(Optional.of(target));

        CommandResult result = handler.handle(command,
                actor(UserType.INTERNAL, true, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM));

        assertThat(result.success()).isTrue();
        assertThat(target.isMultiFirmUser()).isTrue();
        assertThat(target.getLastModifiedBy()).isEqualTo(actorId.toString());
        verify(repository).save(target);
        var order = inOrder(repository, entityManager);
        order.verify(repository).findByEntraOidForUpdate(targetId.toString());
        order.verify(entityManager).refresh(target, LockModeType.PESSIMISTIC_WRITE);
        order.verify(repository).save(target);
    }

    @Test
    void handle_refreshesCachedTargetBeforeCheckingAlreadyConvertedState() {
        EntraUser target = EntraUser.builder().entraOid(targetId.toString()).multiFirmUser(false).build();
        when(repository.findByEntraOidForUpdate(targetId.toString())).thenReturn(Optional.of(target));
        doAnswer(invocation -> {
            target.setMultiFirmUser(true);
            return null;
        }).when(entityManager).refresh(target, LockModeType.PESSIMISTIC_WRITE);

        assertThatThrownBy(() -> handler.handle(command,
                actor(UserType.INTERNAL, true, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void handle_whenAlreadyMultiFirm_throwsConflictAndDoesNotSave() {
        EntraUser target = EntraUser.builder().entraOid(targetId.toString()).multiFirmUser(true).build();
        when(repository.findByEntraOidForUpdate(targetId.toString())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> handler.handle(command,
                actor(UserType.INTERNAL, true, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM)))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void handle_whenTargetMissing_throwsNotFound() {
        when(repository.findByEntraOidForUpdate(targetId.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(command,
                actor(UserType.INTERNAL, true, Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
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
