package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.dto.SetActiveProfileContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.util.Objects;

@Component
public class SetActiveProfilePolicy implements AuthorizationPolicy<SetActiveProfileContext> {

    @Override
    public @NonNull PolicyDecision evaluate(SetActiveProfileContext context, AuthenticatedEntraUser actor) {
        if (!context.profileExists() || !context.profileBelongsToRequestedUser()) {
            return PolicyDecision.allow();
        }

        AuthenticatedUserProfile activeProfile = actor == null ? null : actor.activeProfile();
        if (activeProfile == null || activeProfile.userType() != UserType.EXTERNAL) {
            return PolicyDecision.deny(PolicyDenialReason.ACTOR_NOT_EXTERNAL_USER);
        }

        boolean linkedExternalProfile = actor.userProfiles() != null
                && actor.userProfiles().stream()
                        .anyMatch(profile -> Objects.equals(profile.id(), context.profileId())
                                && profile.userType() == UserType.EXTERNAL);
        if (!linkedExternalProfile) {
            return PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
        }

        return context.profileUserType() == UserType.EXTERNAL
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.TARGET_PROFILE_INTERNAL);
    }

}
