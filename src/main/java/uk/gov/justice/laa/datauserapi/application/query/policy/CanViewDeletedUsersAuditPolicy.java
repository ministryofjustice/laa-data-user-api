package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.application.query.queryaudit.DeletedUsersAuditQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

/** The deleted-users audit list is available to active INTERNAL actors only. */
@Component
public class CanViewDeletedUsersAuditPolicy implements AuthorizationPolicy<DeletedUsersAuditQuery> {

    @Override
    public @NonNull PolicyDecision evaluate(DeletedUsersAuditQuery query, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile profile = actor == null ? null : actor.activeProfile();
        return actor != null && actor.active()
                && profile != null && profile.activeProfile() && profile.userType() == UserType.INTERNAL
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.ACTOR_NOT_ACTIVE_INTERNAL_USER);
    }
}
