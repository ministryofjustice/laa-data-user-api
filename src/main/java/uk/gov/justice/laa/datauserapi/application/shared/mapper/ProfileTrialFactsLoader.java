package uk.gov.justice.laa.datauserapi.application.shared.mapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import uk.gov.justice.laa.datauserapi.application.shared.repository.ProfileTrialFactsRepository;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialOperation;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialRequest;

@Component
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProfileTrialFactsLoader {
    private final ProfileTrialFactsRepository repository;

    public ProfileTrialFactsLoader(ProfileTrialFactsRepository repository) {
        this.repository = repository;
    }

    public ProfileTrialInput load(ProfileTrialRequest request) {
        var principal = request.actor();
        UUID actorProfileId = principal == null || principal.activeProfile() == null ? null : principal.activeProfile().id();
        Set<UUID> ids = new HashSet<>();
        if (actorProfileId != null) {
            ids.add(actorProfileId);
        }
        if (request.profileId() != null) {
            ids.add(request.profileId());
        }
        Map<UUID, UserProfile> profiles = ids.isEmpty() ? Map.of() : repository.findProfiles(ids).stream()
                .collect(Collectors.toMap(UserProfile::getId, profile -> profile));
        UserProfile actorProfile = actorProfileId == null ? null : profiles.get(actorProfileId);
        boolean active = principal != null && principal.id() != null && actorProfile != null && actorProfile.isActiveProfile()
                && actorProfile.getEntraUser().isActive() && principal.id().equals(actorProfile.getEntraUser().getId());
        var actorRoles = active ? actorProfile.getAppRoles() : Set.<AppRole>of();
        List<AppRole> catalogue = repository.findRoleCatalogue();
        Map<UUID, Set<UUID>> restrictions = repository.findRestrictions().stream()
                .collect(Collectors.groupingBy(ProfileTrialFactsRepository.Restriction::roleId,
                        Collectors.mapping(ProfileTrialFactsRepository.Restriction::assigningRoleId, Collectors.toSet())));
        Map<UUID, ProfileTrialInput.Role> roles = catalogue.stream().collect(Collectors.toUnmodifiableMap(
                AppRole::getId, role -> new ProfileTrialInput.Role(
                        role.getId(), role.getApp().getId(), role.getApp().getName(), role.getApp().getEntraAppId(),
                        role.getApp().getEntraOid(), role.getName(), role.getRoleIdentifier(), role.getDescription(),
                        role.getApp().isEnabled(), role.isAuthzRole(), role.isLegacySync(), role.getOrdinal(),
                        values(role.getUserTypeRestriction()), values(role.getFirmTypeRestriction()),
                        Set.copyOf(restrictions.getOrDefault(role.getId(), Set.of())))));
        Set<UUID> authzRoles = actorRoles.stream().filter(AppRole::isAuthzRole)
                .filter(role -> AuthzRole.fromRoleName(role.getName()).isPresent())
                .map(AppRole::getId).collect(Collectors.toUnmodifiableSet());
        Set<UUID> actorRoleIds = actorRoles.stream().map(AppRole::getId).collect(Collectors.toSet());
        var actor = new ProfileTrialInput.Actor(principal == null ? null : principal.id(), actorProfileId,
                principal == null ? null : principal.entraOid(), active ? actorProfile.getUserType() : null,
                !active || actorProfile.getFirm() == null ? null : actorProfile.getFirm().getId(),
                actorRoles.stream().filter(AppRole::isAuthzRole).map(role -> AuthzRole.fromRoleName(role.getName()))
                        .flatMap(java.util.Optional::stream).map(AuthzRole::getRoleName).collect(Collectors.toUnmodifiableSet()),
                authzRoles,
                catalogue.stream().filter(role -> actorRoleIds.contains(role.getId()))
                        .flatMap(role -> role.getPermissions().stream()).collect(Collectors.toUnmodifiableSet()), active);
        UserProfile targetProfile = request.profileId() == null ? null : profiles.get(request.profileId());
        var target = targetProfile == null ? null : new ProfileTrialInput.Target(
                targetProfile.getId(), targetProfile.getUserType(),
                targetProfile.getFirm() == null ? null : targetProfile.getFirm().getId(),
                targetProfile.getFirm() == null ? null : targetProfile.getFirm().getType(),
                targetProfile.getAppRoles().stream().map(AppRole::getId).collect(Collectors.toUnmodifiableSet()));
        Set<UUID> desired = new HashSet<>(request.roleIds());
        Set<UUID> removals = new HashSet<>();
        boolean valid = roles.keySet().containsAll(desired);
        if (request.operation() == ProfileTrialOperation.UPDATE && target != null) {
            desired.clear();
            request.appsAndRoles().values().forEach(desired::addAll);
            valid = request.appsAndRoles().entrySet().stream().allMatch(entry -> roles.values().stream()
                    .anyMatch(role -> role.appId().equals(entry.getKey()))
                    && entry.getValue().stream().allMatch(id -> roles.containsKey(id)
                    && roles.get(id).appId().equals(entry.getKey())));
            removals.addAll(target.roleIds().stream()
                    .filter(id -> roles.containsKey(id) && request.appsAndRoles().containsKey(roles.get(id).appId()))
                    .filter(id -> !desired.contains(id)).toList());
        }
        if (target != null) {
            desired.removeAll(target.roleIds());
        }
        return new ProfileTrialInput(request.operation(), actor, target, roles,
                Set.copyOf(desired), Set.copyOf(removals), valid);
    }

    private static <T> Set<T> values(T[] values) {
        return values == null ? Set.of() : Set.copyOf(Arrays.asList(values));
    }
}
