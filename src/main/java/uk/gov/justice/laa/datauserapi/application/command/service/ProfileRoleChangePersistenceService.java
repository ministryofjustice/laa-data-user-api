package uk.gov.justice.laa.datauserapi.application.command.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.RoleNotificationDetails;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.ProfileRoleChangeRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.ProfileRoleChangeCommand;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialFactsLoader;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialRoleViewMapper;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.UpdateAppRolesResponse;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsNotificationException;
import uk.gov.justice.laa.datauserapi.exception.ProfileRoleConflictException;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialOperation;
import uk.gov.justice.laa.datauserapi.security.rbac.InvalidAuthorizationConfigurationException;

@Service
public class ProfileRoleChangePersistenceService {
    private final ProfileRoleChangeRepository repository;
    private final ProfileTrialFactsLoader loader;
    private final ProfileTrialRoleViewMapper mapper;
    private final EntityManager entityManager;

    public ProfileRoleChangePersistenceService(ProfileRoleChangeRepository repository, ProfileTrialFactsLoader loader,
                                               ProfileTrialRoleViewMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.loader = loader;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Result change(ProfileRoleChangeCommand command) {
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
        var target = profiles.stream().filter(profile -> profile.getId().equals(command.request().profileId()))
                .findFirst().orElseThrow(ProfileRoleConflictException::new);
        Set<UUID> finalRoles = new HashSet<>(current.target().roleIds());
        finalRoles.removeAll(current.removals());
        finalRoles.addAll(current.additions());
        var changes = roleChanges(current, finalRoles);
        final var details = changes.isEmpty() ? null : notificationDetails(target);
        if (!changes.isEmpty()) {
            target.setLastCcmsSyncSuccessful(false);
        }
        target.setAppRoles(roles.stream().filter(role -> finalRoles.contains(role.getId()))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new)));
        entityManager.flush();
        Set<UUID> responseRoles = new HashSet<>(command.request().roleIds());
        command.request().appsAndRoles().values().forEach(responseRoles::addAll);
        var response = new UpdateAppRolesResponse(true, "Roles updated.",
                new AppRoleViewList(responseRoles.stream().map(current.roles()::get)
                        .sorted(Comparator.comparing(ProfileTrialInput.Role::id))
                        .map(mapper::toView).toList()));
        return new Result(response, details, changes);
    }

    private static List<RoleChange> roleChanges(ProfileTrialInput input, Set<UUID> finalRoles) {
        Map<String, Set<String>> before = legacyRoles(input, input.target().roleIds());
        Map<String, Set<String>> after = legacyRoles(input, finalRoles);
        var applications = new TreeSet<String>();
        applications.addAll(before.keySet());
        applications.addAll(after.keySet());
        return applications.stream()
                .filter(application -> !before.getOrDefault(application, Set.of())
                        .equals(after.getOrDefault(application, Set.of())))
                .map(application -> new RoleChange(application, before.getOrDefault(application, Set.of()),
                        after.getOrDefault(application, Set.of())))
                .toList();
    }

    private static Map<String, Set<String>> legacyRoles(ProfileTrialInput input, Set<UUID> ids) {
        var selectedRoles = ids.stream().map(input.roles()::get)
                .filter(ProfileTrialInput.Role::legacySync).toList();
        if (selectedRoles.stream().anyMatch(role -> role.appEntraOid() == null || role.appEntraOid().isBlank()
                || role.identifier() == null || role.identifier().isBlank())) {
            throw new CcmsNotificationException(CcmsNotificationException.Reason.INVALID_PROFILE,
                    "A CCMS role application has no downstream identifier", null);
        }
        return selectedRoles.stream().collect(java.util.stream.Collectors.groupingBy(
                ProfileTrialInput.Role::appEntraOid,
                java.util.stream.Collectors.mapping(ProfileTrialInput.Role::identifier,
                        java.util.stream.Collectors.toSet())));
    }

    private static RoleNotificationDetails notificationDetails(UserProfile profile) {
        var entraUser = profile.getEntraUser();
        var firmCode = profile.getFirm() == null ? null : profile.getFirm().getCode();
        if (profile.getLegacyUserId() == null || (profile.getUserType() == UserType.EXTERNAL && firmCode == null)) {
            throw new CcmsNotificationException(CcmsNotificationException.Reason.INVALID_PROFILE,
                    "A CCMS profile has no downstream identifier or firm code", null);
        }
        return new RoleNotificationDetails(profile.getId(), entraUser.getEntraOid(), profile.getLegacyUserId(),
                profile.getUserType(), firmCode, entraUser.getFirstName(), entraUser.getLastName(), entraUser.getEmail());
    }

    public record Result(UpdateAppRolesResponse response, RoleNotificationDetails profile,
                         List<RoleChange> roleChanges) {
    }

    public record RoleChange(String applicationOid, Set<String> oldRoles, Set<String> newRoles) {
    }
}
