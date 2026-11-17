package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

/**
 * An actor may view a user account when:
 *     they are internal and the target has an internal profile; or
 *     they are external viewing their own account; or
 *     they are external and the target is from their own firm; or
 *     they are internal with {@code VIEW_EXTERNAL_USER} and the target has an external profile.
 * A missing user is denied, so existence is not revealed.
 */
@Component
public class CanViewAccountPolicy implements AuthorizationPolicy<UserAccountVisibilityContext> {

    @Override
    public @NonNull PolicyDecision evaluate(UserAccountVisibilityContext context, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        if (actorProfile == null || actorProfile.userType() == null) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
        }
        if (!context.userExists()) {
            return PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_FOUND);
        }
        return canView(actor, context)
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    private boolean canView(AuthenticatedEntraUser actor, UserAccountVisibilityContext target) {
        return isInternalViewingInternal(actor, target)
                || isExternalViewingOwnAccount(actor, target)
                || isExternalViewingOwnFirm(actor, target)
                || isInternalWithExternalViewPermission(actor, target);
    }

    private boolean isInternalViewingInternal(AuthenticatedEntraUser actor, UserAccountVisibilityContext target) {
        return actor.activeProfile().userType() == UserType.INTERNAL
                && target.hasInternalProfile();
    }

    private boolean isExternalViewingOwnAccount(AuthenticatedEntraUser actor, UserAccountVisibilityContext target) {
        return actor.activeProfile().userType() == UserType.EXTERNAL
                && target.hasExternalProfile()
                && !target.hasInternalProfile()
                && actor.entraOid().equals(target.targetUserOid());
    }

    private boolean isExternalViewingOwnFirm(AuthenticatedEntraUser actor, UserAccountVisibilityContext target) {
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        return actorProfile.userType() == UserType.EXTERNAL
                && actorProfile.firmId() != null
                && target.externalFirmIds() != null
                && target.externalFirmIds().contains(actorProfile.firmId())
                && ActorPermissions.has(actor, Permission.VIEW_EXTERNAL_USER);
    }

    private boolean isInternalWithExternalViewPermission(AuthenticatedEntraUser actor,
                                                         UserAccountVisibilityContext target) {
        return actor.activeProfile().userType() == UserType.INTERNAL
                && target.hasExternalProfile()
                && ActorPermissions.has(actor, Permission.VIEW_EXTERNAL_USER);
    }
}
