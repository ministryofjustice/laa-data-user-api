package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserLifecycleAuthorizationContext;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.opa.OpaAuthorizationClient;
import uk.gov.justice.laa.datauserapi.security.opa.OpaAuthorizationInput;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyFeature;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class OpaLifecycleAuthorizationPolicy implements AuthorizationPolicy<TargetsUser> {

    private final UserCommandService userCommandService;
    private final OpaAuthorizationClient opaAuthorizationClient;

    public OpaLifecycleAuthorizationPolicy(
            UserCommandService userCommandService,
            OpaAuthorizationClient opaAuthorizationClient) {
        this.userCommandService = userCommandService;
        this.opaAuthorizationClient = opaAuthorizationClient;
    }

    @Override
    public AuthorizationPolicyFeature feature() {
        return AuthorizationPolicyFeature.OPA;
    }

    @Override
    public @NonNull PolicyDecision evaluate(TargetsUser command, AuthenticatedEntraUser actor) {
        String action = actionFor(command);
        UserLifecycleAuthorizationContext target =
                userCommandService.getLifecycleAuthorizationContext(command.targetUserEntraObjectId());
        AuthenticatedUserProfile profile = actor.activeProfile();

        OpaAuthorizationInput input = new OpaAuthorizationInput(
                action,
                new OpaAuthorizationInput.Actor(
                        actor.entraOid(),
                        userType(profile == null ? null : profile.userType()),
                        stringValue(profile == null ? null : profile.firmId()),
                        roleNames(profile),
                        permissionNames(actor)),
                new OpaAuthorizationInput.Target(
                        command.targetUserEntraObjectId().toString(),
                        userType(target.userType()),
                        stringValue(target.firmId()),
                        target.multiFirm(),
                        target.active(),
                        target.deactivationType().map(Enum::name).orElse(null)));

        return opaAuthorizationClient.isAllowed(input)
                ? PolicyDecision.allow()
                : PolicyDecision.deny(PolicyDenialReason.POLICY_DENIED);
    }

    private static String actionFor(TargetsUser command) {
        if (command instanceof DeactivateUserCommand) {
            return "deactivate";
        }
        if (command instanceof ReactivateUserCommand) {
            return "reactivate";
        }
        throw new IllegalArgumentException("Unsupported OPA lifecycle command: " + command.getClass().getName());
    }

    private static Set<String> roleNames(AuthenticatedUserProfile profile) {
        return Optional.ofNullable(profile)
                .map(AuthenticatedUserProfile::appRoles)
                .stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(AuthenticatedAppRole::authzRole)
                .map(AuthenticatedAppRole::name)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> permissionNames(AuthenticatedEntraUser actor) {
        return ActorPermissions.of(actor)
                .stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String userType(UserType userType) {
        return userType == null ? null : userType.name();
    }

    private static String stringValue(UUID value) {
        return value == null ? null : value.toString();
    }
}
