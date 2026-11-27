package uk.gov.justice.laa.datauserapi.application.shared.authorisation;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialFactsLoader;
import uk.gov.justice.laa.datauserapi.application.shared.policy.ProfileTrialDecisionPolicy;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialAuthorizer;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialPlan;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

@Component
public class ProfileTrialInputResolver implements AuthorizationPolicyInputResolver<ProfileTrialMessage, ProfileTrialPlan> {
    private final ProfileTrialFactsLoader loader;
    private final ProfileTrialAuthorizer authorizer;

    public ProfileTrialInputResolver(ProfileTrialFactsLoader loader, ProfileTrialAuthorizer authorizer) {
        this.loader = loader;
        this.authorizer = authorizer;
    }

    @Override
    public Class<? extends AuthorizationPolicy<ProfileTrialPlan>> policyType() {
        return ProfileTrialDecisionPolicy.class;
    }

    @Override
    public Class<ProfileTrialMessage> messageType() {
        return ProfileTrialMessage.class;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProfileTrialPlan resolve(ProfileTrialMessage message) {
        ProfileTrialInput input = loader.load(message.request());
        return new ProfileTrialPlan(input, authorizer.decide(input));
    }
}
