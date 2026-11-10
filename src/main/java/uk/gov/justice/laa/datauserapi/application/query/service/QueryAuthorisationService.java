package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;

@Service
@Transactional(readOnly = true)
public class QueryAuthorisationService {

    public AuthenticatedUserProfile requireActiveProfile(AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile profile = actor.activeProfile();
        if (profile == null) {
            throw new InvalidActorContextException("Actor has no active profile: " + actor.entraOid());
        }
        if (profile.userType() == null) {
            throw new InvalidActorContextException("Actor user type cannot be determined");
        }
        return profile;
    }

    public UserProfileDto requireActiveProfile(EntraUserDto actor, String actorOid) {
        UserProfileDto profile = actor.getUserProfiles().stream()
                .filter(UserProfileDto::isActiveProfile)
                .findFirst()
                .orElseThrow(() -> new InvalidActorContextException("Actor has no active profile: " + actorOid));
        if (profile.getUserType() == null) {
            throw new InvalidActorContextException("Actor user type cannot be determined");
        }
        return profile;
    }

    /**
     * Preserves account-level visibility, which differs from profile visibility: internal accounts do not require
     * VIEW_INTERNAL_USER, and same-firm account access can match a target profile of either user type.
     */
    public boolean canViewAccount(UserProfileDto actorProfile, EntraUserDto target) {
        if (actorProfile.getUserType() == UserType.INTERNAL
                && target.getUserProfiles().stream().anyMatch(profile -> profile.getUserType() == UserType.INTERNAL)) {
            return true;
        }
        if (actorProfile.getUserType() == UserType.EXTERNAL
                && actorProfile.getFirm() != null
                && target.getUserProfiles().stream().anyMatch(profile ->
                profile.getFirm() != null && profile.getFirm().getId().equals(actorProfile.getFirm().getId()))) {
            return true;
        }
        boolean canViewExternalUsers = actorProfile.getAppRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);
        return actorProfile.getUserType() == UserType.INTERNAL && canViewExternalUsers
                && target.getUserProfiles().stream().anyMatch(profile -> profile.getUserType() == UserType.EXTERNAL);
    }

    /**
     * Checks if the authenticated actor can view the target profile (using AuthenticatedUserProfile).
     * Authorisation rules:
     * 1. INTERNAL actors can view INTERNAL profiles (with VIEW_INTERNAL_USER permission)
     * 2. EXTERNAL actors can view profiles in their own firm
     * 3. INTERNAL actors with VIEW_EXTERNAL_USER permission can view EXTERNAL profiles
     */
    public boolean canViewProfile(AuthenticatedUserProfile actorProfile, UserType targetUserType, String targetFirmId) {
        if (isInternalViewingInternal(actorProfile, targetUserType)) {
            return true;
        }

        if (isExternalViewingOwnFirm(actorProfile, targetFirmId)) {
            return true;
        }

        return isInternalWithExternalViewPermission(actorProfile, targetUserType);
    }

    private boolean isInternalViewingInternal(AuthenticatedUserProfile actorProfile, UserType targetUserType) {
        return actorProfile.userType() == UserType.INTERNAL 
                && targetUserType == UserType.INTERNAL
                && actorProfile.appRoles().stream()
                    .flatMap(role -> role.permissions().stream())
                    .anyMatch(permission -> permission == Permission.VIEW_INTERNAL_USER);
    }


    private boolean isExternalViewingOwnFirm(AuthenticatedUserProfile actorProfile, String targetFirmId) {
        return actorProfile.userType() == UserType.EXTERNAL 
                && actorProfile.firmId() != null
                && targetFirmId != null
                && actorProfile.firmId().toString().equals(targetFirmId);
    }

    private boolean isInternalWithExternalViewPermission(AuthenticatedUserProfile actorProfile, UserType targetUserType) {
        boolean canViewExternalUsers = actorProfile.appRoles().stream()
                .flatMap(role -> role.permissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);

        return actorProfile.userType() == UserType.INTERNAL 
                && canViewExternalUsers 
                && targetUserType == UserType.EXTERNAL;
    }
}
