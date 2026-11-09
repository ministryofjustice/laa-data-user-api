package uk.gov.justice.laa.datauserapi.application.command.service;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserDeactivationContext;
import uk.gov.justice.laa.datauserapi.application.command.policy.ReactivationHierarchyPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

@Component
public class ReactivationPolicyInputResolver
        implements AuthorizationPolicyInputResolver<TargetsUser, UserDeactivationContext> {

    private final UserCommandService userCommandService;

    public ReactivationPolicyInputResolver(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @Override
    public Class<? extends AuthorizationPolicy<UserDeactivationContext>> policyType() {
        return ReactivationHierarchyPolicy.class;
    }

    @Override
    public Class<TargetsUser> messageType() {
        return TargetsUser.class;
    }

    @Override
    public UserDeactivationContext resolve(TargetsUser message) {
        return userCommandService.getDeactivationContext(message.targetUserEntraObjectId());
    }
}
