package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.GlobalAuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUserProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Firm scoping for commands that {@link TargetsUserProfile}: any non-internal actor may only act on a profile
 * belonging to their own firm. Internal (LAA) actors are not firm-scoped.
 */
@Component
public class ProviderAdminProfileFirmScopePolicy implements GlobalAuthorizationPolicy<TargetsUserProfile> {

    private final UserCommandService userCommandService;

    public ProviderAdminProfileFirmScopePolicy(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @Override
    public boolean appliesTo(TargetsUserProfile message, AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile profile = actor.activeProfile();
        return profile == null || profile.userType() != UserType.INTERNAL;
    }

    @Override
    public PolicyDecision evaluate(TargetsUserProfile message, AuthenticatedEntraUser actor) {
        UUID actorFirmId = Optional.ofNullable(actor.activeProfile()).map(AuthenticatedUserProfile::firmId).orElse(null);
        if (actorFirmId == null) {
            return PolicyDecision.deny("provider admin has no active firm");
        }
        return userCommandService.findProfileFirmId(message.targetUserProfileId()).filter(actorFirmId::equals).isPresent()
                ? PolicyDecision.allow()
                : PolicyDecision.deny("target profile is not in provider admin's firm");
    }
}
