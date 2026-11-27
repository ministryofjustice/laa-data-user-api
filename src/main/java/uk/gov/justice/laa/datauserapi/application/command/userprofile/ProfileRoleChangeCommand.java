package uk.gov.justice.laa.datauserapi.application.command.userprofile;

import uk.gov.justice.laa.datauserapi.application.shared.authorisation.ProfileTrialMessage;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialPlan;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialRequest;
import uk.gov.justice.laa.datauserapi.application.shared.policy.ProfileTrialDecisionPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

@PolicyGuarded(policies = ProfileTrialDecisionPolicy.class)
public record ProfileRoleChangeCommand(ProfileTrialRequest request, ProfileTrialPlan plan) implements ProfileTrialMessage {
    @Override
    public ProfileRoleChangeCommand withAuthorizationContext(ProfileTrialPlan context) {
        return new ProfileRoleChangeCommand(request, context);
    }
}
