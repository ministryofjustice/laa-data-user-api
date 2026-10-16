package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;

@Service
@Transactional(readOnly = true)
public class QueryProfileAuthorisationHandler {

    /**
     * Checks if the actor can view the target profile.
     * Authorisation rules:
     * 1. INTERNAL actors can view INTERNAL profiles
     * 2. EXTERNAL actors can view profiles in their own firm
     * 3. INTERNAL actors with VIEW_EXTERNAL_USER permission can view EXTERNAL profiles
     */
    public boolean handle(UserProfile actorProfile, UserProfile targetProfile) {
        if (isInternalViewingInternal(actorProfile, targetProfile.getUserType())) {
            return true;
        }

        if (isExternalViewingOwnFirm(actorProfile, targetProfile.getFirm())) {
            return true;
        }

        return isInternalWithExternalViewPermission(actorProfile, targetProfile.getUserType());
    }

    /**
     * Checks if the actor can view the target profile summary (used for list responses).
     * Authorisation rules: same as canViewProfile above.
     */
    public boolean handle(UserProfile actorProfile, UserProfileSummaryView targetProfile) {
        if (isInternalViewingInternal(actorProfile, targetProfile.userType())) {
            return true;
        }

        if (isExternalViewingOwnFirmSummary(actorProfile, targetProfile.firmId())) {
            return true;
        }

        return isInternalWithExternalViewPermission(actorProfile, targetProfile.userType());
    }

    private boolean isInternalViewingInternal(UserProfile actorProfile, UserType targetUserType) {
        return actorProfile.getUserType() == UserType.INTERNAL 
                && targetUserType == UserType.INTERNAL
                && actorProfile.getAppRoles().stream()
                    .flatMap(role -> role.getPermissions().stream())
                    .anyMatch(permission -> permission == Permission.VIEW_INTERNAL_USER);
    }

    private boolean isExternalViewingOwnFirm(UserProfile actorProfile, uk.gov.justice.laa.datauserapi.entity.Firm targetFirm) {
        return actorProfile.getUserType() == UserType.EXTERNAL 
                && actorProfile.getFirm() != null
                && targetFirm != null
                && actorProfile.getFirm().getId().equals(targetFirm.getId());
    }

    private boolean isExternalViewingOwnFirmSummary(UserProfile actorProfile, String targetFirmId) {
        return actorProfile.getUserType() == UserType.EXTERNAL 
                && actorProfile.getFirm() != null
                && targetFirmId != null
                && actorProfile.getFirm().getId().toString().equals(targetFirmId);
    }

    private boolean isInternalWithExternalViewPermission(UserProfile actorProfile, UserType targetUserType) {
        boolean canViewExternalUsers = actorProfile.getAppRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);

        return actorProfile.getUserType() == UserType.INTERNAL 
                && canViewExternalUsers 
                && targetUserType == UserType.EXTERNAL;
    }
}
