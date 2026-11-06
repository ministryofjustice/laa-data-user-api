package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

/**
 * Validates that the actor has a valid profile for performing user-targeting queries.
 * Checks that the actor has an active profile with a recognized user type.
 *
 * This policy is generic and reusable for any query message type.
 */
@Component
public class ActorProfileValidationPolicy implements AuthorizationPolicy<Object> {

    @Override
    public @NonNull PolicyDecision evaluate(Object message, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile profile = actor.activeProfile();
        if (profile == null) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
        }

        UserType userType = profile.userType();
        if (userType == null) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_USER_TYPE_UNDETERMINED);
        }

        return PolicyDecision.allow();
    }
}
