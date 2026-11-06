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
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

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
public class TargetUserVisibilityPolicy implements AuthorizationPolicy<TargetsUser> {

    private final TargetUserResolver targetUserResolver;

    public TargetUserVisibilityPolicy(TargetUserResolver targetUserResolver) {
        this.targetUserResolver = targetUserResolver;
    }

    @Override
    public @NonNull PolicyDecision evaluate(TargetsUser message, AuthenticatedEntraUser actor) {
        EntraUserDto targetUser = targetUserResolver.resolveFor(message);

        if (targetUser == null) {
            return PolicyDecision.deny("target user not found");
        }

        // Determine target user type
        UserType targetType = determineTargetUserType(targetUser);
        if (targetType == null) {
            return PolicyDecision.deny("target user has no valid profiles");
        }

        // Check visibility based on actor and target types
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        UserType actorType = actorProfile.userType();
        Set<Permission> actorPermissions = ActorPermissions.of(actor);

        // Internal actor viewing internal user
        if (targetType == UserType.INTERNAL && actorType == UserType.INTERNAL) {
            return actorPermissions.contains(Permission.VIEW_INTERNAL_USER)
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny("actor lacks VIEW_INTERNAL_USER permission");
        }

        // Internal actor viewing external or multi-firm user
        if ((targetType == UserType.EXTERNAL || targetUser.isMultiFirmUser()) && actorType == UserType.INTERNAL) {
            return actorPermissions.contains(Permission.VIEW_EXTERNAL_USER)
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny("actor lacks VIEW_EXTERNAL_USER permission");
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
                    : PolicyDecision.deny("external actor cannot view user from different firm");
        }

        // External actor cannot view internal or multi-firm users
        if (actorType == UserType.EXTERNAL) {
            return PolicyDecision.deny("external actor can only view external users from their firm");
        }

        return PolicyDecision.deny("cannot determine visibility");
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
