package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.UUID;

/** An actor may not perform the guarded action on their own account. */
@Component
public class NotSelfTargetPolicy implements AuthorizationPolicy<TargetsUser> {

    @Override
    public @NonNull PolicyDecision evaluate(TargetsUser targetsUser, AuthenticatedEntraUser actor) {
        UUID targetId = targetsUser.targetUserEntraObjectId();
        return targetId != null && targetId.toString().equalsIgnoreCase(actor.entraOid())
                ? PolicyDecision.deny(PolicyMessages.SELF_TARGET_NOT_ALLOWED)
                : PolicyDecision.allow();
    }
}
