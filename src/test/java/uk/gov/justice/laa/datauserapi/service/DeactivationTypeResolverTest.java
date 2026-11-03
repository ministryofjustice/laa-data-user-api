package uk.gov.justice.laa.datauserapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

class DeactivationTypeResolverTest {

    private final DeactivationTypeResolver resolver = new DeactivationTypeResolver();

    static List<Arguments> roleCases() {
        return List.of(
                Arguments.of(List.of(AuthzRole.GLOBAL_ADMIN.getRoleName()), DeactivationType.PRIVILEGED),
                Arguments.of(List.of(AuthzRole.SECURITY_RESPONSE.getRoleName()), DeactivationType.PRIVILEGED),
                Arguments.of(List.of(AuthzRole.EXTERNAL_USER_MANAGER.getRoleName()), DeactivationType.LAA),
                Arguments.of(List.of(AuthzRole.EXTERNAL_USER_ADMIN.getRoleName()), DeactivationType.LAA),
                Arguments.of(List.of(AuthzRole.FIRM_USER_MANAGER.getRoleName()), DeactivationType.FIRM),
                Arguments.of(List.of("Some Other Role"), DeactivationType.NONE),
                Arguments.of(List.of(AuthzRole.FIRM_USER_MANAGER.getRoleName(),
                        AuthzRole.EXTERNAL_USER_ADMIN.getRoleName(),
                        AuthzRole.GLOBAL_ADMIN.getRoleName()), DeactivationType.PRIVILEGED),
                Arguments.of(List.of(AuthzRole.FIRM_USER_MANAGER.getRoleName(),
                        AuthzRole.EXTERNAL_USER_MANAGER.getRoleName()), DeactivationType.LAA));
    }

    @ParameterizedTest
    @MethodSource("roleCases")
    void resolveFromRolesReturnsHighestDelegation(List<String> roles, DeactivationType expected) {
        assertThat(resolver.resolveFromRoles(roles)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("roleCases")
    void resolveUsesActiveProfileRoles(List<String> roles, DeactivationType expected) {
        assertThat(resolver.resolve(actor(profile(roles.toArray(String[]::new))))).isEqualTo(expected);
    }

    @Test
    void resolveFromRolesReturnsNoneForNullOrEmpty() {
        assertThat(resolver.resolveFromRoles(null)).isEqualTo(DeactivationType.NONE);
        assertThat(resolver.resolveFromRoles(List.of())).isEqualTo(DeactivationType.NONE);
    }

    @Test
    void resolveReturnsNoneForNullActor() {
        assertThat(resolver.resolve(null)).isEqualTo(DeactivationType.NONE);
    }

    @Test
    void resolveReturnsNoneWhenNoActiveProfile() {
        assertThat(resolver.resolve(actor(null))).isEqualTo(DeactivationType.NONE);
    }

    @Test
    void resolveReturnsNoneWhenActiveProfileHasNoRoles() {
        assertThat(resolver.resolve(actor(profile()))).isEqualTo(DeactivationType.NONE);
    }

    private static AuthenticatedEntraUser actor(AuthenticatedUserProfile activeProfile) {
        return AuthenticatedEntraUser.builder().id(UUID.randomUUID()).activeProfile(activeProfile).build();
    }

    private static AuthenticatedUserProfile profile(String... roleNames) {
        Set<AuthenticatedAppRole> roles = Arrays.stream(roleNames)
                .map(name -> new AuthenticatedAppRole(UUID.randomUUID(), name, 0, null, false,
                        null, null, null, true, null, Set.of()))
                .collect(Collectors.toSet());
        return new AuthenticatedUserProfile(UUID.randomUUID(), true, UserType.EXTERNAL, null, null, null,
                null, null, null, false, false, Set.of(), roles);
    }
}
