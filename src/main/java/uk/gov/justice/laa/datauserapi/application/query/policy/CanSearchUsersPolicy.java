package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserSearchAuthorizationContext;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

/**
 * An actor may search users when:
 * <ul>
 *     <li>they have an active profile; and</li>
 *     <li>their user type is determined.</li>
 * </ul>
 * Missing or undetermined actor context is denied.
 */
@Component
public class CanSearchUsersPolicy implements AuthorizationPolicy<UserSearchAuthorizationContext> {

    @Override
    public @NonNull PolicyDecision evaluate(UserSearchAuthorizationContext context, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        if (actorProfile == null || actorProfile.userType() == null) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
        }
        return PolicyDecision.allow();
    }
}
