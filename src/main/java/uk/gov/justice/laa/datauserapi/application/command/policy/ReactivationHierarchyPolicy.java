package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces that the target is currently deactivated and that the actor's roles are high enough to undo the
 * way it was deactivated:
 *
 * <table>
 *   <caption>Who may reactivate, by deactivation type</caption>
 *   <tr><th>type</th><th>FUM</th><th>EUM / EUA / EUS</th><th>SR / GA</th></tr>
 *   <tr><td>none recorded (legacy)</td><td>no</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>NONE</td><td>yes</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>SYNC, LAA</td><td>no</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>FIRM</td><td>yes (same firm, enforced by the firm scope policy)</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>PRIVILEGED</td><td>no</td><td>no</td><td>yes</td></tr>
 * </table>
 */
@Component
public class ReactivationHierarchyPolicy implements AuthorizationPolicy<TargetsUser> {

    private final UserCommandService userCommandService;

    public ReactivationHierarchyPolicy(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @Override
    public PolicyDecision evaluate(TargetsUser message, AuthenticatedEntraUser actor) {
        if (!userCommandService.isDeactivated(message.targetUserEntraObjectId())) {
            return PolicyDecision.deny("target user is not deactivated");
        }
        DeactivationType type = userCommandService.findDeactivationType(message.targetUserEntraObjectId()).orElse(null);
        return canReactivate(type, roleNames(actor))
                ? PolicyDecision.allow()
                : PolicyDecision.deny("actor roles cannot reactivate a user deactivated with type " + type);
    }

    static boolean canReactivate(DeactivationType type, Set<String> roles) {
        boolean privileged = has(roles, AuthzRole.GLOBAL_ADMIN) || has(roles, AuthzRole.SECURITY_RESPONSE);
        boolean externalUserManagement = has(roles, AuthzRole.EXTERNAL_USER_ADMIN)
                || has(roles, AuthzRole.EXTERNAL_USER_MANAGER)
                || has(roles, AuthzRole.EXTERNAL_USER_SUPPORT);
        if (type == null) {
            return privileged || externalUserManagement || has(roles, AuthzRole.INTERNAL_USER_MANAGER);
        }
        return switch (type) {
            case NONE -> true;
            case SYNC, LAA -> privileged || externalUserManagement;
            case FIRM -> privileged || externalUserManagement || has(roles, AuthzRole.FIRM_USER_MANAGER);
            case PRIVILEGED -> privileged;
        };
    }

    private static boolean has(Set<String> roles, AuthzRole role) {
        return roles.contains(role.getRoleName());
    }

    private static Set<String> roleNames(AuthenticatedEntraUser actor) {
        return Optional.ofNullable(actor.activeProfile())
                .map(AuthenticatedUserProfile::appRoles)
                .stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(AuthenticatedAppRole::authzRole)
                .map(AuthenticatedAppRole::name)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
