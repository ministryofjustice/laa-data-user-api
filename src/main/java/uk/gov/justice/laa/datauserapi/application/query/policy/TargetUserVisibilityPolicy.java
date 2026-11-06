package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.util.Set;
import java.util.UUID;

/**
 * Validates that the actor can view a specific target user based on user types and permissions.
 * Enforces visibility rules:
 * - Internal actors can view internal users with VIEW_INTERNAL_USER permission
 * - Internal actors can view external/multi-firm users with VIEW_EXTERNAL_USER permission
 * - External actors can only view users from their own firm
 * - Multi-firm users (no profiles) are treated as external
 *
 * This policy is reusable for any query message that targets a specific user (implements TargetsUser).
 * 
 * Uses TargetUserResolver to load target user facts (pure policy, no direct repository access).
 */
@Component
public class TargetUserVisibilityPolicy implements AuthorizationPolicy<EntraUserDto> {

    @Override
    public @NonNull PolicyDecision evaluate(EntraUserDto targetUser, AuthenticatedEntraUser actor) {
        if (targetUser == null) {
            return PolicyDecision.deny(PolicyDenialReason.TARGET_USER_NOT_FOUND);
        }

        // Determine target user type
        UserType targetType = determineTargetUserType(targetUser);
        if (targetType == null) {
            return PolicyDecision.deny(PolicyDenialReason.TARGET_USER_NO_VALID_PROFILES);
        }

        // Check visibility based on actor and target types
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        UserType actorType = actorProfile.userType();
        Set<Permission> actorPermissions = ActorPermissions.of(actor);

        // Internal actor viewing internal user
        if (targetType == UserType.INTERNAL && actorType == UserType.INTERNAL) {
            return actorPermissions.contains(Permission.VIEW_INTERNAL_USER)
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny(PolicyDenialReason.ACTOR_LACKS_PERMISSION);
        }

        // Internal actor viewing external or multi-firm user
        if ((targetType == UserType.EXTERNAL || targetUser.isMultiFirmUser()) && actorType == UserType.INTERNAL) {
            return actorPermissions.contains(Permission.VIEW_EXTERNAL_USER)
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny(PolicyDenialReason.ACTOR_LACKS_PERMISSION);
        }

        // External actor viewing external user from same firm
        if (targetType == UserType.EXTERNAL && actorType == UserType.EXTERNAL) {
            UUID actorFirmId = actorProfile.firmId();
            UUID targetFirmId = targetUser.getUserProfiles().stream()
                    .filter(UserProfileDto::isActiveProfile)
                    .findFirst()
                    .map(p -> p.getFirm() != null ? p.getFirm().getId() : null)
                    .orElse(null);

            return actorFirmId != null && actorFirmId.equals(targetFirmId)
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny(PolicyDenialReason.TARGET_USER_OUTSIDE_ACTOR_FIRM);
        }

        // External actor cannot view internal or multi-firm users
        if (actorType == UserType.EXTERNAL) {
            return PolicyDecision.deny(PolicyDenialReason.POLICY_DENIED);
        }

        return PolicyDecision.deny(PolicyDenialReason.POLICY_DENIED);
    }

    private UserType determineTargetUserType(EntraUserDto targetUser) {
        Set<UserProfileDto> profiles = targetUser.getUserProfiles();

        // Multi-firm user: no profiles but marked as multi-firm
        if (profiles.isEmpty() && targetUser.isMultiFirmUser()) {
            return UserType.EXTERNAL;  // Treat as external
        }

        // Invalid state: no profiles and not multi-firm
        if (profiles.isEmpty()) {
            return null;
        }

        // Regular user: get type from active profile
        return profiles.stream()
                .filter(UserProfileDto::isActiveProfile)
                .findFirst()
                .map(UserProfileDto::getUserType)
                .orElse(null);
    }
}
