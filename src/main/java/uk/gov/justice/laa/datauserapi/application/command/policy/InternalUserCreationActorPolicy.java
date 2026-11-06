package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.util.Objects;

@Component
public class InternalUserCreationActorPolicy implements AuthorizationPolicy<CreateUserAccountCommand> {
    @Override
    public @NonNull PolicyDecision evaluate(CreateUserAccountCommand command, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile profile = actor == null ? null : actor.activeProfile();
        if (command == null || actor == null || !actor.active() || profile == null || !profile.activeProfile()
                || profile.userType() != UserType.INTERNAL || !hasAuthorizedPermission(profile)) {
            return PolicyDecision.deny(PolicyDenialReason.CREATE_EXTERNAL_USER_ACTOR_NOT_ELIGIBLE);
        }
        return PolicyDecision.allow();
    }

    private boolean hasAuthorizedPermission(AuthenticatedUserProfile profile) {
        return profile.appRoles() != null && profile.appRoles().stream()
                .filter(Objects::nonNull)
                .filter(AuthenticatedAppRole::authzRole)
                .anyMatch(role -> role.permissions() != null
                        && role.permissions().contains(Permission.CREATE_EXTERNAL_USER));
    }
}
