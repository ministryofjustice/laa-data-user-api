package uk.gov.justice.laa.datauserapi.application.command.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.SetActiveProfileContext;
import uk.gov.justice.laa.datauserapi.application.command.policy.SetActiveProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

@Component
public class SetActiveProfilePolicyInputResolver
        implements AuthorizationPolicyInputResolver<SetActiveProfileCommand, SetActiveProfileContext> {

    private final UserProfileCommandRepository repository;

    public SetActiveProfilePolicyInputResolver(UserProfileCommandRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<? extends AuthorizationPolicy<SetActiveProfileContext>> policyType() {
        return SetActiveProfilePolicy.class;
    }

    @Override
    public Class<SetActiveProfileCommand> messageType() {
        return SetActiveProfileCommand.class;
    }

    @Override
    @Transactional(readOnly = true)
    public SetActiveProfileContext resolve(SetActiveProfileCommand command) {
        return repository.findForSetActiveProfileAuthorization(command.userProfileId())
                .map(profile -> new SetActiveProfileContext(
                        true,
                        profile.getId(),
                        profile.getEntraUser().getEntraOid()
                                .equalsIgnoreCase(command.userEntraObjectId().toString()),
                        profile.getUserType()))
                .orElseGet(SetActiveProfileContext::missing);
    }
}
