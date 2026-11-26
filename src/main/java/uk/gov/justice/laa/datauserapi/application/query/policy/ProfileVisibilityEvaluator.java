package uk.gov.justice.laa.datauserapi.application.query.policy;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;

/**
 * Shared visibility evaluation logic for profile authorization policies.
 * Encapsulates the rules for determining whether an actor may view a specific profile.
 */
public class ProfileVisibilityEvaluator {

    private ProfileVisibilityEvaluator() {
    }

    /**
     * Evaluates whether an actor may view a profile based on the target's visibility context.
     * 
     * Rules:
     * <ul>
     *     <li>internal users viewing internal profiles require VIEW_INTERNAL_USER permission</li>
     *     <li>external users may always view their own profile</li>
     *     <li>external users may view other profiles in their own firm if they have VIEW_EXTERNAL_USER</li>
     *     <li>internal users may view external profiles if they have VIEW_EXTERNAL_USER</li>
     * </ul>
     */
    public static boolean isProfileVisible(AuthenticatedEntraUser actor, ProfileVisibilityContext target) {
        return isInternalViewingInternal(actor, target)
                || isExternalViewingOwnProfile(actor.activeProfile(), target)
                || isExternalViewingOwnFirm(actor, target)
                || isInternalWithExternalViewPermission(actor, target);
    }

    private static boolean isInternalViewingInternal(AuthenticatedEntraUser actor, ProfileVisibilityContext target) {
        return actor.activeProfile().userType() == UserType.INTERNAL
                && target.userType() == UserType.INTERNAL
                && ActorPermissions.has(actor, Permission.VIEW_INTERNAL_USER);
    }

    private static boolean isExternalViewingOwnProfile(AuthenticatedUserProfile actorProfile,
                                                       ProfileVisibilityContext target) {
        return actorProfile.userType() == UserType.EXTERNAL
                && actorProfile.id() != null
                && actorProfile.id().equals(target.profileId());
    }

    private static boolean isExternalViewingOwnFirm(AuthenticatedEntraUser actor, ProfileVisibilityContext target) {
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        return actorProfile.userType() == UserType.EXTERNAL
                && actorProfile.firmId() != null
                && actorProfile.firmId().equals(target.firmId())
                && ActorPermissions.has(actor, Permission.VIEW_EXTERNAL_USER);
    }

    private static boolean isInternalWithExternalViewPermission(AuthenticatedEntraUser actor,
                                                                 ProfileVisibilityContext target) {
        return actor.activeProfile().userType() == UserType.INTERNAL
                && target.userType() == UserType.EXTERNAL
                && ActorPermissions.has(actor, Permission.VIEW_EXTERNAL_USER);
    }
}
