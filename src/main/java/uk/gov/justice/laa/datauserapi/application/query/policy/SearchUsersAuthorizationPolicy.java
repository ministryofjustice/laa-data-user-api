package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;

import java.util.Set;

/**
 * Authorization policy for searching user accounts. Enforces the following rules:
 * - Actor must have active profile with valid user type
 * - Internal actors must have at least VIEW_INTERNAL_USER or VIEW_EXTERNAL_USER permission
 * - External actors must have an assigned firm for scoping
 * - Multi-firm users in results are treated as external (filtered by VIEW_EXTERNAL_USER permission)
 */
@Component
public class SearchUsersAuthorizationPolicy implements AuthorizationPolicy<SearchUsersQuery> {

    @Override
    public @NonNull PolicyDecision evaluate(SearchUsersQuery query, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile actorProfile = actor.activeProfile();
        if (actorProfile == null) {
            return PolicyDecision.deny("actor has no active profile");
        }

        UserType actorUserType = actorProfile.userType();
        if (actorUserType == null) {
            return PolicyDecision.deny("actor user type cannot be determined");
        }

        Set<Permission> actorPermissions = ActorPermissions.of(actor);

        // Internal actors must have at least one view permission
        if (actorUserType == UserType.INTERNAL) {
            boolean canViewInternal = actorPermissions.contains(Permission.VIEW_INTERNAL_USER);
            boolean canViewExternal = actorPermissions.contains(Permission.VIEW_EXTERNAL_USER);
            
            if (!canViewInternal && !canViewExternal) {
                return PolicyDecision.deny("internal actor lacks both VIEW_INTERNAL_USER and VIEW_EXTERNAL_USER permissions");
            }
            return PolicyDecision.allow();
        }

        // External actors must have assigned firm for scoping
        if (actorUserType == UserType.EXTERNAL) {
            return actorProfile.firmId() != null
                    ? PolicyDecision.allow()
                    : PolicyDecision.deny("external actor has no assigned firm");
        }

        return PolicyDecision.deny("actor user type is not recognized");
    }
}
