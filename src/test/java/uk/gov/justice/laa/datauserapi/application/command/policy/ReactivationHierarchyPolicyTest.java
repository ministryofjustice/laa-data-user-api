package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserDeactivationContext;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReactivationHierarchyPolicyTest {

    @Test
    void deniesTargetThatIsNotDeactivated() {
        assertThat(policy().evaluate(new UserDeactivationContext(false, Optional.empty()),
                actorWithRole(AuthzRole.GLOBAL_ADMIN)).reason()).isEqualTo(PolicyDenialReason.TARGET_USER_NOT_DEACTIVATED);
    }

    @Test
    void deniesActorWithoutActiveProfile() {
        assertThat(policy().evaluate(context(DeactivationType.NONE),
                AuthenticatedEntraUser.builder().build()).granted()).isTrue();
        assertThat(policy().evaluate(context(DeactivationType.LAA),
                AuthenticatedEntraUser.builder().build()).reason())
                .isEqualTo(PolicyDenialReason.REACTIVATION_NOT_ALLOWED_FOR_DEACTIVATION_TYPE);
    }

    @ParameterizedTest
    @CsvSource({
        "NONE,FIRM_USER_MANAGER,true",
        "NONE,INTERNAL_USER_MANAGER,true",
        "SYNC,FIRM_USER_MANAGER,false",
        "SYNC,EXTERNAL_USER_SUPPORT,true",
        "LAA,FIRM_USER_MANAGER,false",
        "LAA,EXTERNAL_USER_MANAGER,true",
        "FIRM,FIRM_USER_MANAGER,true",
        "FIRM,INTERNAL_USER_MANAGER,false",
        "PRIVILEGED,EXTERNAL_USER_ADMIN,false",
        "PRIVILEGED,EXTERNAL_USER_MANAGER,false",
        "PRIVILEGED,SECURITY_RESPONSE,true",
        "PRIVILEGED,GLOBAL_ADMIN,true",
        "SYNC,GLOBAL_ADMIN,true",
        "SYNC,SECURITY_RESPONSE,true",
        "SYNC,EXTERNAL_USER_ADMIN,true"
    })
    void appliesHierarchyByDeactivationType(DeactivationType type, AuthzRole role, boolean expected) {
        assertThat(policy().evaluate(context(type), actorWithRole(role)).granted()).isEqualTo(expected);
        assertThat(ReactivationHierarchyPolicy.canReactivate(type, actorWithRole(role))).isEqualTo(expected);
    }

    @Test
    void ignoresNonAuthorizationRoleWithPrivilegedRoleName() {
        AuthenticatedAppRole nonAuthorizationRole = new AuthenticatedAppRole(
                UUID.randomUUID(), AuthzRole.GLOBAL_ADMIN.getRoleName(), 0, null, false,
                null, null, "application role", false, null, Set.of());
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .activeProfile(AuthenticatedUserProfile.builder().appRoles(Set.of(nonAuthorizationRole)).build())
                .build();

        assertThat(policy().evaluate(context(DeactivationType.PRIVILEGED), actor).granted()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "INTERNAL_USER_MANAGER,true",
        "EXTERNAL_USER_ADMIN,true",
        "EXTERNAL_USER_SUPPORT,true",
        "GLOBAL_ADMIN,true",
        "FIRM_USER_MANAGER,false",
        "INTERNAL_USER_VIEWER,false"
    })
    void legacyDeactivationsOnlyAllowInternalOrExternalManagement(AuthzRole role, boolean expected) {
        assertThat(policy().evaluate(context(null), actorWithRole(role)).granted()).isEqualTo(expected);
        assertThat(ReactivationHierarchyPolicy.canReactivate(null, actorWithRole(role))).isEqualTo(expected);
    }

    private ReactivationHierarchyPolicy policy() {
        return new ReactivationHierarchyPolicy();
    }

    private static UserDeactivationContext context(DeactivationType type) {
        return new UserDeactivationContext(true, Optional.ofNullable(type));
    }

    private static AuthenticatedEntraUser actorWithRole(AuthzRole authzRole) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), authzRole.getRoleName(), 0, null, false,
                null, null, "desc", true, null, Set.of());
        return AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .activeProfile(AuthenticatedUserProfile.builder().activeProfile(true).appRoles(Set.of(role)).build())
                .build();
    }
}
