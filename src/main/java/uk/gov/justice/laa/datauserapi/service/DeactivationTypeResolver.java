package uk.gov.justice.laa.datauserapi.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
            log.warn("Deactivation type resolution received no actor",
                    kv("deactivationType", DeactivationType.NONE));
            return DeactivationType.NONE;
        }

        if (actor.activeProfile() == null) {
            log.warn("Deactivation type resolution received no active profile",
                    kv("actorEntraOid", actor.entraOid()),
                    kv("deactivationType", DeactivationType.NONE));
            return DeactivationType.NONE;
        }

        Set<AuthzRole> roles = actor.activeProfile().appRoles().stream()
                .filter(Objects::nonNull)
                .filter(AuthenticatedAppRole::authzRole)
                .map(AuthenticatedAppRole::name)
                .map(AuthzRole::fromRoleName)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        return resolveFromRoles(roles);
    }

    /**
     * Pure, stateless form of the resolver — suitable for direct testing.
     *
     * @param roles the authorization roles the disabling user currently holds
     * @return the appropriate {@link DeactivationType} (never {@code null})
     */
    public DeactivationType resolveFromRoles(Set<AuthzRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return DeactivationType.NONE;
        }

        // Check from highest to lowest delegation
        if (roles.contains(AuthzRole.GLOBAL_ADMIN)
                || roles.contains(AuthzRole.SECURITY_RESPONSE)) {
            return DeactivationType.PRIVILEGED;
        }

        if (roles.contains(AuthzRole.EXTERNAL_USER_MANAGER)
                || roles.contains(AuthzRole.EXTERNAL_USER_ADMIN)) {
            return DeactivationType.LAA;
        }

        if (roles.contains(AuthzRole.FIRM_USER_MANAGER)) {
            return DeactivationType.FIRM;
        }

        return DeactivationType.NONE;
    }
}
