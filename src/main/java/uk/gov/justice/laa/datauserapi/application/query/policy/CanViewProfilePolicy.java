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
 * An actor may view a profile when:
 * <ul>
 *     <li>they are external and it is their own active profile;</li>
 *     <li>they are external with {@code VIEW_EXTERNAL_USER} and it belongs to their own firm;</li>
 *     <li>they are internal with {@code VIEW_INTERNAL_USER} and it is an internal profile; or</li>
 *     <li>they are internal with {@code VIEW_EXTERNAL_USER} and it is an external profile.</li>
 * </ul>
 * A missing profile is denied, so existence is not revealed.
 *
 * <p>Being internal is not enough to view other internal users: per the SiLAS roles matrix, External User Manager and
 * External User Admin are internal roles without {@code VIEW_INTERNAL_USER}.
 *
 * <p>This is deliberately stricter than {@code QueryAuthorisationService.canViewProfile}, which the profile detail
 * queries use: that rule lets any external actor view profiles in their own firm without a permission. The two agree
 * for internal actors, and a test keeps them in step.
 */
@Component
public class CanViewProfilePolicy implements AuthorizationPolicy<ProfileVisibilityContext> {

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
