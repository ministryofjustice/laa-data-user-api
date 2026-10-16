package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;

@Service
@Transactional(readOnly = true)
public class QueryAuthorisationService {

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
