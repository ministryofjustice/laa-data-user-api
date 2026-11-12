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
        String targetFirmId = context.firmId() == null ? null : context.firmId().toString();
        return canView(actorProfile, context.userType(), targetFirmId)
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    /** The single definition of profile visibility, also used by the profile detail query. */
    public static boolean canView(AuthenticatedUserProfile actorProfile, UserType targetUserType, String targetFirmId) {
        return isInternalViewingInternal(actorProfile, targetUserType)
                || isExternalViewingOwnFirm(actorProfile, targetFirmId)
                || isInternalWithExternalViewPermission(actorProfile, targetUserType);
    }

    private static boolean isInternalViewingInternal(AuthenticatedUserProfile actorProfile, UserType targetUserType) {
        return actorProfile.userType() == UserType.INTERNAL
                && targetUserType == UserType.INTERNAL
                && holds(actorProfile, Permission.VIEW_INTERNAL_USER);
    }

    private static boolean isExternalViewingOwnFirm(AuthenticatedUserProfile actorProfile, String targetFirmId) {
        return actorProfile.userType() == UserType.EXTERNAL
                && actorProfile.firmId() != null
                && targetFirmId != null
                && actorProfile.firmId().toString().equals(targetFirmId);
    }

    private static boolean isInternalWithExternalViewPermission(AuthenticatedUserProfile actorProfile,
                                                                UserType targetUserType) {
        return actorProfile.userType() == UserType.INTERNAL
                && targetUserType == UserType.EXTERNAL
                && holds(actorProfile, Permission.VIEW_EXTERNAL_USER);
    }

    private static boolean holds(AuthenticatedUserProfile actorProfile, Permission permission) {
        return actorProfile.appRoles().stream()
                .flatMap(role -> role.permissions().stream())
                .anyMatch(granted -> granted == permission);
    }
}
