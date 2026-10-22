package uk.gov.justice.laa.datauserapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

import java.util.List;

/**
 * Resolves the {@link DeactivationType} that should be recorded when an external user is deactivated.
 *
 * <p>The type is determined from the highest-delegation role held by the disabling user at
 * the moment of the deactivation action. The hierarchy (highest to lowest delegation) is:
 * <ol>
 *   <li>Global Admin or Security Response → {@link DeactivationType#PRIVILEGED}</li>
 *   <li>External User Manager or External User Admin → {@link DeactivationType#LAA}</li>
 *   <li>Firm User Manager → {@link DeactivationType#FIRM}</li>
 *   <li>Any other actor (automated sync, unknown) → {@link DeactivationType#NONE}</li>
 * </ol>
 *
 * <p>A {@code NULL} value is reserved for legacy/unknown cases where the disabling actor
 * cannot be determined (e.g. pre-existing deactivated accounts).  This class will never return
 * {@code null}; callers must explicitly pass {@code null} when no actor is known.
 */
@Slf4j
@Component
public class DeactivationTypeResolver {

    /**
     * Resolves the deactivate type from the actor's current active user profile roles.
     *
     * @param actor the AuthenticatedEntraUser performing the deactivation action
     * @return the appropriate {@link DeactivationType} (never {@code null})
     */
    public DeactivationType resolve(AuthenticatedEntraUser actor) {
        if (actor == null) {
            log.warn("DeactivationTypeResolver.resolve called with null actor — returning NONE");
            return DeactivationType.NONE;
        }

        if (actor.activeProfile() == null) {
            log.warn("DeactivationTypeResolver.resolve called with null activeProfile — returning NONE");
            return DeactivationType.NONE;
        }

        List<String> roleNames = actor.activeProfile().appRoles().stream()
                .map(AuthenticatedAppRole::name)
                .toList();

        return resolveFromRoles(roleNames);
    }

    /**
     * Pure, stateless form of the resolver — suitable for direct testing.
     *
     * @param roleNames the role names the disabling user currently holds
     * @return the appropriate {@link DeactivationType} (never {@code null})
     */
    public DeactivationType resolveFromRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return DeactivationType.NONE;
        }

        // Check from highest to lowest delegation
        if (roleNames.contains(AuthzRole.GLOBAL_ADMIN.getRoleName())
                || roleNames.contains(AuthzRole.SECURITY_RESPONSE.getRoleName())) {
            return DeactivationType.PRIVILEGED;
        }

        if (roleNames.contains(AuthzRole.EXTERNAL_USER_MANAGER.getRoleName())
                || roleNames.contains(AuthzRole.EXTERNAL_USER_ADMIN.getRoleName())) {
            return DeactivationType.LAA;
        }

        if (roleNames.contains(AuthzRole.FIRM_USER_MANAGER.getRoleName())) {
            return DeactivationType.FIRM;
        }

        return DeactivationType.NONE;
    }
}

