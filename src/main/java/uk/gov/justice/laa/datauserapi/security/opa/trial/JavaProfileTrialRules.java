package uk.gov.justice.laa.datauserapi.security.opa.trial;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialDecision.Filters;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialDecision.Reason;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput.Role;

/** Java comparator/fallback for the trial; evaluates only immutable facts. */
@Component
public class JavaProfileTrialRules {
    public ProfileTrialDecision evaluate(ProfileTrialInput input) {
        var actor = input.actor();
        if (!actor.active() || actor.userType() == null) {
            return ProfileTrialDecision.deny(Reason.ACTOR_INACTIVE);
        }
        if (!input.validRequest()) {
            return ProfileTrialDecision.deny(Reason.INVALID_REQUEST);
        }
        Set<UserType> types = new HashSet<>();
        if (actor.permissions().contains(Permission.VIEW_EXTERNAL_USER)) {
            types.add(UserType.EXTERNAL);
        }
        if (actor.userType() == UserType.INTERNAL && actor.permissions().contains(Permission.VIEW_INTERNAL_USER)) {
            types.add(UserType.INTERNAL);
        }
        UUID firmId = actor.userType() == UserType.EXTERNAL ? actor.firmId() : null;
        if (actor.userType() == UserType.EXTERNAL && firmId == null) {
            return ProfileTrialDecision.deny(Reason.FIRM_SCOPE);
        }
        if (input.operation() == ProfileTrialOperation.SEARCH) {
            return types.isEmpty() ? ProfileTrialDecision.deny(Reason.MISSING_PERMISSION)
                    : new ProfileTrialDecision(true, Reason.ALLOWED, new Filters(firmId, types, Set.of()));
        }
        if (input.target() == null) {
            return ProfileTrialDecision.deny(Reason.TARGET_NOT_FOUND);
        }
        if (actor.userType() == UserType.EXTERNAL
                && (input.target().userType() != UserType.EXTERNAL || !firmId.equals(input.target().firmId()))) {
            return ProfileTrialDecision.deny(Reason.FIRM_SCOPE);
        }
        if (input.operation() == ProfileTrialOperation.DETAIL) {
            return types.contains(input.target().userType())
                    ? new ProfileTrialDecision(true, Reason.ALLOWED, new Filters(firmId, types, Set.of()))
                    : ProfileTrialDecision.deny(Reason.MISSING_PERMISSION);
        }
        Permission assignPermission = input.target().userType() == UserType.INTERNAL
                ? Permission.ASSIGN_INTERNAL_USER_ROLES : Permission.ASSIGN_EXTERNAL_USER_ROLES;
        Permission removePermission = input.target().userType() == UserType.INTERNAL
                ? Permission.REMOVE_INTERNAL_USER_ROLES : Permission.REMOVE_EXTERNAL_USER_ROLES;
        if (input.operation() == ProfileTrialOperation.ASSIGNABLE) {
            if (!actor.permissions().contains(assignPermission)) {
                return ProfileTrialDecision.deny(Reason.MISSING_PERMISSION);
            }
            Set<UUID> roles = input.roles().values().stream()
                    .filter(role -> compatible(role, input))
                    .filter(role -> assignable(role, input))
                    .map(Role::id).collect(Collectors.toUnmodifiableSet());
            return new ProfileTrialDecision(true, Reason.ALLOWED,
                    new Filters(firmId, Set.of(input.target().userType()), roles));
        }
        if ((!input.additions().isEmpty() && !actor.permissions().contains(assignPermission))
                || (!input.removals().isEmpty() && !actor.permissions().contains(removePermission))) {
            return ProfileTrialDecision.deny(Reason.MISSING_PERMISSION);
        }
        if (input.additions().stream().anyMatch(id -> !compatible(input.roles().get(id), input))) {
            return ProfileTrialDecision.deny(Reason.INCOMPATIBLE_ROLE);
        }
        if (input.additions().stream().anyMatch(id -> !assignable(input.roles().get(id), input))) {
            return ProfileTrialDecision.deny(Reason.ASSIGNMENT_RESTRICTED);
        }
        return new ProfileTrialDecision(true, Reason.ALLOWED, new Filters(firmId, Set.of(input.target().userType()), Set.of()));
    }

    private static boolean compatible(Role role, ProfileTrialInput input) {
        return role != null && role.enabled()
                && (role.userTypes().isEmpty() || role.userTypes().contains(input.target().userType()))
                && (role.firmTypes().isEmpty() || (input.target().firmType() != null
                && role.firmTypes().contains(input.target().firmType())));
    }

    private static boolean assignable(Role role, ProfileTrialInput input) {
        return role != null && (role.assigners().isEmpty()
                || role.assigners().stream().anyMatch(input.actor().roleIds()::contains));
    }
}
