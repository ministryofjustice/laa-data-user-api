package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUserProfile;

/** An actor may not perform the guarded action on any profile belonging to their own account. */
@Component
public class NotSelfProfileTargetPolicy implements AuthorizationPolicy<TargetsUserProfile> {

    private final UserCommandService userCommandService;

    public NotSelfProfileTargetPolicy(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @Override
    public @NonNull PolicyDecision evaluate(TargetsUserProfile targetsUserProfile, AuthenticatedEntraUser actor) {
        return userCommandService.findProfileEntraOid(targetsUserProfile.targetUserProfileId())
                .filter(oid -> oid.equalsIgnoreCase(actor.entraOid()))
                .isPresent()
                ? PolicyDecision.deny(PolicyMessages.SELF_TARGET_NOT_ALLOWED)
                : PolicyDecision.allow();
    }
}
