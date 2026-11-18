package uk.gov.justice.laa.datauserapi.application.command.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.ProfileRoleChangeRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.ProfileRoleChangeCommand;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialFactsLoader;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialRoleViewMapper;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.UpdateAppRolesResponse;
import uk.gov.justice.laa.datauserapi.application.command.dto.RoleNotificationDetails;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput;
import uk.gov.justice.laa.datauserapi.exception.ProfileRoleConflictException;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialOperation;
import uk.gov.justice.laa.datauserapi.security.rbac.InvalidAuthorizationConfigurationException;

@Service
public class ProfileRoleChangeService {
    private final ProfileRoleChangeRepository repository;
    private final ProfileTrialFactsLoader loader;
    private final RoleChangeOutboxWriter outbox;
    private final ProfileTrialRoleViewMapper mapper;
    private final EntityManager entityManager;

    public ProfileRoleChangeService(ProfileRoleChangeRepository repository, ProfileTrialFactsLoader loader,
                                    RoleChangeOutboxWriter outbox, ProfileTrialRoleViewMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.loader = loader;
        this.outbox = outbox;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public UpdateAppRolesResponse change(ProfileRoleChangeCommand command) {
        if (command.request().operation() != ProfileTrialOperation.ASSIGN
                && command.request().operation() != ProfileTrialOperation.UPDATE) {
            throw new InvalidAuthorizationConfigurationException("A read was routed to the role-change service");
        }
        entityManager.clear();
        Set<UUID> ids = new HashSet<>(List.of(command.plan().input().actor().profileId(), command.request().profileId()));
        var profiles = repository.findProfilesForUpdate(ids);
        final var roles = repository.findRolesForUpdate();
        var current = loader.load(command.request());
        if (!current.equals(command.plan().input())) {
            throw new ProfileRoleConflictException();
        }
        var target = profiles.stream().filter(p -> p.getId().equals(command.request().profileId()))
                .findFirst().orElseThrow(ProfileRoleConflictException::new);
        Set<UUID> finalRoles = new HashSet<>(current.target().roleIds());
        finalRoles.removeAll(current.removals());
        finalRoles.addAll(current.additions());
        var targetUser = target.getEntraUser();
        var details = new RoleNotificationDetails(
                target.getId(), target.getLegacyUserId(), target.getUserType(),
                target.getFirm() == null ? null : target.getFirm().getCode(),
                targetUser.getFirstName(), targetUser.getLastName(), targetUser.getEmail());
        if (outbox.enqueue(details, current, finalRoles)) {
            target.setLastCcmsSyncSuccessful(false);
        }
        target.setAppRoles(roles.stream().filter(role -> finalRoles.contains(role.getId()))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new)));
        entityManager.flush();
        Set<UUID> responseRoles = new HashSet<>(command.request().roleIds());
        command.request().appsAndRoles().values().forEach(responseRoles::addAll);
        return new UpdateAppRolesResponse(true, "Roles updated.",
                new AppRoleViewList(responseRoles.stream().map(current.roles()::get)
                        .sorted(Comparator.comparing(ProfileTrialInput.Role::id))
                        .map(mapper::toView).toList()));
    }
}
