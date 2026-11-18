package uk.gov.justice.laa.datauserapi.application.shared.authorisation;

import uk.gov.justice.laa.datauserapi.application.shared.policy.ProfileTrialDecisionPolicy;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialPlan;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialRequest;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.ResolvedAuthorizationMessage;

public interface ProfileTrialMessage extends ResolvedAuthorizationMessage<ProfileTrialPlan> {
    ProfileTrialRequest request();

    ProfileTrialPlan plan();

    @Override
    default Class<ProfileTrialPlan> authorizationContextType() {
        return ProfileTrialPlan.class;
    }

    @Override
    default Class<? extends AuthorizationPolicy<ProfileTrialPlan>> contextPolicyType() {
        return ProfileTrialDecisionPolicy.class;
    }
}
