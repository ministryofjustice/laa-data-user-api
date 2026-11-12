package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

/**
 * An actor may view a profile when they are external and it belongs to their own firm, or internal with
 * {@code VIEW_INTERNAL_USER} for an internal profile, or internal with {@code VIEW_EXTERNAL_USER} for an
 * external profile. A missing profile is denied, so existence is not revealed.
 *
 * <p>Being internal is not enough to view other internal users: per the SiLAS roles matrix, External User Manager and
 * External User Admin are internal roles without {@code VIEW_INTERNAL_USER}. The rule mirrors
 * {@code QueryAuthorisationService.canViewProfile}, which the profile detail queries use; a parity test keeps the two
 * in step.
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
        return canView(actorProfile, context)
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    private boolean canView(AuthenticatedUserProfile actorProfile, ProfileVisibilityContext target) {
        return isInternalViewingInternal(actorProfile, target)
                || isExternalViewingOwnFirm(actorProfile, target)
                || isInternalWithExternalViewPermission(actorProfile, target);
    }

    private boolean isInternalViewingInternal(AuthenticatedUserProfile actorProfile, ProfileVisibilityContext target) {
        return actorProfile.userType() == UserType.INTERNAL
                && target.userType() == UserType.INTERNAL
                && holds(actorProfile, Permission.VIEW_INTERNAL_USER);
    }

    private boolean isExternalViewingOwnFirm(AuthenticatedUserProfile actorProfile, ProfileVisibilityContext target) {
        return actorProfile.userType() == UserType.EXTERNAL
                && actorProfile.firmId() != null
                && actorProfile.firmId().equals(target.firmId());
    }

    private boolean isInternalWithExternalViewPermission(AuthenticatedUserProfile actorProfile,
                                                         ProfileVisibilityContext target) {
        return actorProfile.userType() == UserType.INTERNAL
                && target.userType() == UserType.EXTERNAL
                && holds(actorProfile, Permission.VIEW_EXTERNAL_USER);
    }

    private boolean holds(AuthenticatedUserProfile actorProfile, Permission permission) {
        return actorProfile.appRoles().stream()
                .flatMap(role -> role.permissions().stream())
                .anyMatch(granted -> granted == permission);
    }
}
