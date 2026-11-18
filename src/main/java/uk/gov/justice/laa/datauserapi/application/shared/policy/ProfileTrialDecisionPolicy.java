package uk.gov.justice.laa.datauserapi.application.shared.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialPlan;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyFeature;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

@Component
public class ProfileTrialDecisionPolicy implements AuthorizationPolicy<ProfileTrialPlan> {
    @Override
    public AuthorizationPolicyFeature feature() {
        return AuthorizationPolicyFeature.ALWAYS;
    }

    @Override
    public @NonNull PolicyDecision evaluate(ProfileTrialPlan plan, AuthenticatedEntraUser actor) {
        return plan != null && plan.input() != null && plan.input().actor() != null && plan.decision() != null
                && actor != null && actor.id() != null
                && actor.id().equals(plan.input().actor().id()) && plan.decision().allow()
                ? PolicyDecision.allow() : PolicyDecision.deny(PolicyDenialReason.POLICY_DENIED);
    }
}
