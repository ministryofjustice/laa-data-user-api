package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.GlobalAuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.Optional;
import java.util.UUID;

/**
 * Firm scoping for Provider Admins. Applied automatically to every guarded command that
 * {@link TargetsUser} when the actor is a Provider Admin, i.e. any external actor; internal (LAA)
 * actors are not firm-scoped. A Provider Admin may only act on a user who:
 * <ul>
 *     <li>is not a multi-firm user, and</li>
 *     <li>has an active profile in the Provider Admin's own firm.</li>
 * </ul>
 */
@Component
public class ProviderAdminFirmScopePolicy implements GlobalAuthorizationPolicy<TargetsUser> {

    private final UserCommandService userCommandService;

    public ProviderAdminFirmScopePolicy(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @Override
    public boolean appliesTo(TargetsUser message, AuthenticatedEntraUser actor) {
        // Anything other than a confirmed internal actor is scoped, so a missing profile fails closed.
        return !isInternal(actor);
    }

    @Override
    public PolicyDecision evaluate(TargetsUser message, AuthenticatedEntraUser actor) {
        UUID actorFirmId = Optional.ofNullable(actor.activeProfile()).map(AuthenticatedUserProfile::firmId).orElse(null);
        if (actorFirmId == null) {
            return PolicyDecision.deny("provider admin has no active firm");
        }

        UUID targetId = message.targetUserEntraObjectId();
        if (userCommandService.isMultiFirmUser(targetId)) {
            return PolicyDecision.deny("provider admin cannot act on multi-firm user");
        }
        return userCommandService.findActiveFirmId(targetId).filter(actorFirmId::equals).isPresent()
                ? PolicyDecision.allow()
                : PolicyDecision.deny("target user is not in provider admin's firm");
    }

    private static boolean isInternal(AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile profile = actor.activeProfile();
        return profile != null && profile.userType() == UserType.INTERNAL;
    }
}
