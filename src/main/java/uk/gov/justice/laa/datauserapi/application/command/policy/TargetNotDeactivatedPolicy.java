package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

/** A user that is already deactivated cannot be deactivated again. */
@Component
public class TargetNotDeactivatedPolicy implements AuthorizationPolicy<TargetsUser> {

    private final UserCommandService userCommandService;

    public TargetNotDeactivatedPolicy(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @Override
    public PolicyDecision evaluate(TargetsUser message, AuthenticatedEntraUser actor) {
        return userCommandService.isDeactivated(message.targetUserEntraObjectId())
                ? PolicyDecision.deny("target user is already deactivated")
                : PolicyDecision.allow();
    }
}
