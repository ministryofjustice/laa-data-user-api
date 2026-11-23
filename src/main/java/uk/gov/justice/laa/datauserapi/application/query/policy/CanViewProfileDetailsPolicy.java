package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

/**
 * Authorization policy for viewing a single user profile's details (getActiveProfile). The list query
 * (getUserProfiles) is guarded separately by {@link CanViewProfileListPolicy}.
 * Uses the same visibility logic as {@link CanViewProfilePolicy}.
 *
 * An actor may view a profile when:
 * <ul>
 *     <li>they are external and it is their own active profile;</li>
 *     <li>they are external with {@code VIEW_EXTERNAL_USER} and it belongs to their own firm;</li>
 *     <li>they are internal with {@code VIEW_INTERNAL_USER} and it is an internal profile; or</li>
 *     <li>they are internal with {@code VIEW_EXTERNAL_USER} and it is an external profile.</li>
 * </ul>
 * A missing profile is denied, so existence is not revealed.
 */
@Component
public class CanViewProfileDetailsPolicy implements AuthorizationPolicy<ProfileVisibilityContext> {

    @Override
    public @NonNull PolicyDecision evaluate(ProfileVisibilityContext context, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        if (actorProfile == null || actorProfile.userType() == null) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
        }
        if (!context.profileExists()) {
            return PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_FOUND);
        }
        return ProfileVisibilityEvaluator.isProfileVisible(actor, context)
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }
}
