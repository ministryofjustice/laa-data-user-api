package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.GlobalAuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsFirm;

import java.util.Optional;

/**
 * Firm scoping for commands that {@link TargetsFirm}: any non-internal actor may only act on their own firm.
 * Internal (LAA) actors are not firm-scoped.
 */
@Component
public class ProviderAdminOwnFirmPolicy implements GlobalAuthorizationPolicy<TargetsFirm> {

    @Override
    public boolean appliesTo(TargetsFirm message, AuthenticatedEntraUser actor) {
        // Anything other than a confirmed internal actor is scoped, so a missing profile fails closed.
        AuthenticatedUserProfile profile = actor.activeProfile();
        return profile == null || profile.userType() != UserType.INTERNAL;
    }

    @Override
    public @NonNull PolicyDecision evaluate(TargetsFirm message, AuthenticatedEntraUser actor) {
        String actorFirmCode = Optional.ofNullable(actor.activeProfile())
                .map(AuthenticatedUserProfile::firmCode)
                .orElse(null);
        if (actorFirmCode == null) {
            return PolicyDecision.deny(PolicyMessages.PROVIDER_ADMIN_NO_ACTIVE_FIRM);
        }
        return actorFirmCode.equalsIgnoreCase(message.targetFirmCode())
                ? PolicyDecision.allow()
                : PolicyDecision.deny("target firm is not the provider admin's firm");
    }
}
