package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;
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
        return canView(actor, context)
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    private boolean canView(AuthenticatedEntraUser actor, ProfileVisibilityContext target) {
        return isInternalViewingInternal(actor, target)
                || isExternalViewingOwnFirm(actor.activeProfile(), target)
                || isInternalWithExternalViewPermission(actor, target);
    }

    private boolean isInternalViewingInternal(AuthenticatedEntraUser actor, ProfileVisibilityContext target) {
        return actor.activeProfile().userType() == UserType.INTERNAL
                && target.userType() == UserType.INTERNAL
                && ActorPermissions.has(actor, Permission.VIEW_INTERNAL_USER);
    }

    private boolean isExternalViewingOwnFirm(AuthenticatedUserProfile actorProfile, ProfileVisibilityContext target) {
        return actorProfile.userType() == UserType.EXTERNAL
                && actorProfile.firmId() != null
                && actorProfile.firmId().equals(target.firmId());
    }

    private boolean isInternalWithExternalViewPermission(AuthenticatedEntraUser actor,
                                                         ProfileVisibilityContext target) {
        return actor.activeProfile().userType() == UserType.INTERNAL
                && target.userType() == UserType.EXTERNAL
                && ActorPermissions.has(actor, Permission.VIEW_EXTERNAL_USER);
    }
}
