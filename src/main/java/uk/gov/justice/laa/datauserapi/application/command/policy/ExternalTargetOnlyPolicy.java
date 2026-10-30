package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

/** The guarded action is only permitted on external users; internal (LAA) users are never a valid target. */
@Component
public class ExternalTargetOnlyPolicy implements AuthorizationPolicy<TargetsUser> {

    private final UserCommandService userCommandService;

    public ExternalTargetOnlyPolicy(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @Override
    public @NonNull PolicyDecision evaluate(TargetsUser targetsUser, AuthenticatedEntraUser actor) {
        return userCommandService.isInternalUser(targetsUser.targetUserEntraObjectId())
                ? PolicyDecision.deny("target user is an internal user")
                : PolicyDecision.allow();
    }
}
