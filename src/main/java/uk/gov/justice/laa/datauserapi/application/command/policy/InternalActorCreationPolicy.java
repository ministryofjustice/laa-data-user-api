package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

@Component
public class InternalActorCreationPolicy implements AuthorizationPolicy<CreateUserAccountCommand> {

    @Override
    public @NonNull PolicyDecision evaluate(CreateUserAccountCommand command, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile profile = actor == null ? null : actor.activeProfile();
        return command != null && actor != null && actor.active()
                && profile != null && profile.activeProfile() && profile.userType() == UserType.INTERNAL
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.ACTOR_NOT_ACTIVE_INTERNAL_USER);
    }
}
