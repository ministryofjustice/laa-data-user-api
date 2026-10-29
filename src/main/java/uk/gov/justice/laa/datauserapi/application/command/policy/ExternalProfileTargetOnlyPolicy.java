package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUserProfile;

/** The guarded action is only permitted on external user profiles; internal (LAA) profiles are never a valid target. */
@Component
public class ExternalProfileTargetOnlyPolicy implements AuthorizationPolicy<TargetsUserProfile> {

    private final UserCommandService userCommandService;

    public ExternalProfileTargetOnlyPolicy(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @Override
    public PolicyDecision evaluate(TargetsUserProfile message, AuthenticatedEntraUser actor) {
        return userCommandService.findProfileUserType(message.targetUserProfileId())
                .filter(UserType.INTERNAL::equals)
                .isPresent()
                ? PolicyDecision.deny("target profile belongs to an internal user")
                : PolicyDecision.allow();
    }
}
