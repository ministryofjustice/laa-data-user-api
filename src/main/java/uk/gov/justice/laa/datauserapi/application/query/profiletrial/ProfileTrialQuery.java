package uk.gov.justice.laa.datauserapi.application.query.profiletrial;

import uk.gov.justice.laa.datauserapi.contracts.dto.ProfileSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.shared.authorisation.ProfileTrialMessage;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialPlan;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialRequest;
import uk.gov.justice.laa.datauserapi.application.shared.policy.ProfileTrialDecisionPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

@PolicyGuarded(policies = ProfileTrialDecisionPolicy.class)
public record ProfileTrialQuery(ProfileTrialRequest request, ProfileSearchCriteria criteria, ProfileTrialPlan plan)
        implements ProfileTrialMessage {
    @Override
    public ProfileTrialQuery withAuthorizationContext(ProfileTrialPlan context) {
        return new ProfileTrialQuery(request, criteria, context);
    }
}
