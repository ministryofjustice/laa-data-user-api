package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Authorization policy for viewing user accounts. Enforces the following rules:
 * - Multi-firm users are always treated as external
 * - Internal actors can view internal users (with VIEW_INTERNAL_USER permission)
 * - External actors can view users from the same firm
 * - Internal actors with VIEW_EXTERNAL_USER permission can view external/multi-firm users
 */
@Component
public class GetUserAccountAuthorizationPolicy implements AuthorizationPolicy<TargetsUser> {

    private final UserAccountQueryService userAccountQueryService;

    public GetUserAccountAuthorizationPolicy(UserAccountQueryService userAccountQueryService) {
        this.userAccountQueryService = userAccountQueryService;
    }

    @Override
    public @NonNull PolicyDecision evaluate(TargetsUser message, AuthenticatedEntraUser actor) {
        UUID targetUserEntraObjectId = message.targetUserEntraObjectId();
        EntraUserDto targetUser = userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                targetUserEntraObjectId.toString());

        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        if (actorProfile == null) {
            return PolicyDecision.deny("actor has no active profile");
        }

        Set<Permission> actorPermissions = ActorPermissions.of(actor);

        // Multi-firm users are always treated as external
        if (targetUser.isMultiFirmUser()) {
            return canViewExternalUser(actorProfile, actorPermissions);
        }

        // Get the target user's type
        UserType targetUserType = targetUser.getUserProfiles().stream()
                .filter(profile -> profile.isActiveProfile())
                .map(profile -> profile.getUserType())
                .findFirst()
                .orElse(null);

        if (targetUserType == null) {
            return PolicyDecision.deny("target user has no active profile");
        }

        // Internal actor viewing internal user
        if (actorProfile.userType() == UserType.INTERNAL && targetUserType == UserType.INTERNAL) {
            return actorPermissions.contains(Permission.VIEW_INTERNAL_USER)
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny("internal actor lacks VIEW_INTERNAL_USER permission");
        }

        // External actor viewing same firm
        if (actorProfile.userType() == UserType.EXTERNAL && targetUserType == UserType.EXTERNAL) {
            return isSameFirm(actorProfile, targetUser)
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny("target user is from a different firm");
        }

        // Internal actor viewing external user
        if (actorProfile.userType() == UserType.INTERNAL && targetUserType == UserType.EXTERNAL) {
            return canViewExternalUser(actorProfile, actorPermissions);
        }

        // External actor cannot view internal user
        return PolicyDecision.deny("external actor cannot view internal user");
    }

    private PolicyDecision canViewExternalUser(AuthenticatedUserProfile actorProfile,
                                              Set<Permission> actorPermissions) {
        if (actorProfile.userType() != UserType.INTERNAL) {
            return PolicyDecision.deny("non-internal actor cannot view external users");
        }
        return actorPermissions.contains(Permission.VIEW_EXTERNAL_USER)
                ? PolicyDecision.allow()
                : PolicyDecision.deny("actor lacks VIEW_EXTERNAL_USER permission");
    }

    private boolean isSameFirm(AuthenticatedUserProfile actorProfile, EntraUserDto targetUser) {
        UUID actorFirmId = Optional.ofNullable(actorProfile.firmId()).orElse(null);
        if (actorFirmId == null) {
            return false;
        }

        UUID targetFirmId = targetUser.getUserProfiles().stream()
                .filter(profile -> profile.isActiveProfile())
                .map(profile -> profile.getFirm())
                .filter(firm -> firm != null)
                .map(firm -> firm.getId())
                .findFirst()
                .orElse(null);

        return targetFirmId != null && actorFirmId.equals(targetFirmId);
    }
}
