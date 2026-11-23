package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.util.List;

/**
 * Authorization policy for viewing a list of user profiles (getUserProfiles).
 * Evaluates whether the actor may view at least one profile in the requested list.
 * 
 * The policy applies the same per-profile visibility rules as {@link CanViewProfileDetailsPolicy},
 * but operates on a list of profiles.
 * 
 * An actor may view the list when they can view at least one profile:
 * <ul>
 *     <li>they are external and can view their own active profile;</li>
 *     <li>they are external with {@code VIEW_EXTERNAL_USER} and can view profiles in their own firm;</li>
 *     <li>they are internal with {@code VIEW_INTERNAL_USER} and can view internal profiles; or</li>
 *     <li>they are internal with {@code VIEW_EXTERNAL_USER} and can view external profiles.</li>
 * </ul>
 * 
 * Missing actor active profile or no visible profiles in the list both return a uniform denial.
 */
@Component
public class CanViewProfileListPolicy implements AuthorizationPolicy<List<ProfileVisibilityContext>> {

    @Override
    public @NonNull PolicyDecision evaluate(List<ProfileVisibilityContext> contexts, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        if (actorProfile == null || actorProfile.userType() == null) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
        }
        
        if (contexts.isEmpty()) {
            return PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_FOUND);
        }
        
        boolean anyVisible = contexts.stream()
                .filter(ProfileVisibilityContext::profileExists)
                .anyMatch(context -> ProfileVisibilityEvaluator.isProfileVisible(actor, context));
        
        return anyVisible
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }
}
