package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportAuthorizationContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

@Component
public class CanExportAuditUsersPolicy implements AuthorizationPolicy<AuditUserExportAuthorizationContext> {

    @Override
    public @NonNull PolicyDecision evaluate(AuditUserExportAuthorizationContext context, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        if (actorProfile == null || actorProfile.userType() == null) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
        }

        if (!ActorPermissions.has(actor, Permission.EXPORT_AUDIT_DATA)) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_NO_EXPORT_PERMISSION);
        }

        if (!context.scopedToOneFirmOrInternalOnly()) {
            return PolicyDecision.deny(PolicyDenialReason.EXPORT_SCOPE_NOT_CONSTRAINED);
        }

        if (context.requestsInternalOnly()) {
            if (actorProfile.userType() != UserType.INTERNAL
                    || !ActorPermissions.has(actor, Permission.VIEW_INTERNAL_USER)) {
                return PolicyDecision.deny(PolicyDenialReason.ACTOR_NO_VIEW_PERMISSION);
            }
            return PolicyDecision.allow();
        }

        if (!ActorPermissions.has(actor, Permission.VIEW_EXTERNAL_USER)) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_NO_VIEW_PERMISSION);
        }

        if (actorProfile.userType() == UserType.EXTERNAL
                && !actorFirmMatches(actorProfile, context.requestedFirmId())) {
            return PolicyDecision.deny(PolicyDenialReason.TARGET_FIRM_OUTSIDE_ACTOR_FIRM);
        }

        return PolicyDecision.allow();
    }

    private boolean actorFirmMatches(AuthenticatedUserProfile actorProfile, String requestedFirmId) {
        return actorProfile.firmCode() != null && actorProfile.firmCode().equals(requestedFirmId);
    }
}
