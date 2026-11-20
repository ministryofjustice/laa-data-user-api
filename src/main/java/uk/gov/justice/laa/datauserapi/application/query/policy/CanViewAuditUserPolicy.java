package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;

/**
 * An actor may view a user's audit detail only when they could view that user's account, so the audit endpoint
 * never exposes an account the caller cannot otherwise see. See {@link CanViewAccountPolicy}.
 */
@Component
public class CanViewAuditUserPolicy implements AuthorizationPolicy<UserAccountVisibilityContext> {

    private final CanViewAccountPolicy accountPolicy;

    public CanViewAuditUserPolicy(CanViewAccountPolicy accountPolicy) {
        this.accountPolicy = accountPolicy;
    }

    @Override
    public @NonNull PolicyDecision evaluate(UserAccountVisibilityContext context, AuthenticatedEntraUser actor) {
        return accountPolicy.evaluate(context, actor);
    }
}
