package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfileDetailsPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsVisibilityPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyAuthorizationDenial;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves the GetActiveProfileHandler is authorised by {@link CanViewProfileDetailsPolicy} through the real Spring
 * proxies: the repository is stubbed with the target's facts, so removing the policy would let every case pass.
 */
@SpringJUnitConfig(GetActiveProfileHandlerAuthorizationIntegrationTest.TestConfig.class)
class GetActiveProfileHandlerAuthorizationIntegrationTest {

    private static final UUID TARGET_PROFILE_ID = UUID.randomUUID();
    private static final UUID TARGET_FIRM_ID = UUID.randomUUID();
    private static final String TARGET_USER_ENTRA_OID = UUID.randomUUID().toString();

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({
        RbacMethodSecurityConfig.class,
        GetActiveProfileHandler.class,
        CanViewProfileDetailsPolicy.class,
        UserProfileDetailsVisibilityPolicyInputResolver.class
    })
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        UserProfileQueryRepository userProfileQueryRepository() {
            return mock(UserProfileQueryRepository.class);
        }

        @Bean
        UserProfileDetailsService userProfileDetailsService() {
            return mock(UserProfileDetailsService.class);
        }
    }

    @Autowired
    private GetActiveProfileHandler handler;

    @Autowired
    private UserProfileDetailsService service;

    @Autowired
    private UserProfileQueryRepository repository;

    @BeforeEach
    void setUp() {
        reset(service, repository);
    }

    @Test
    void actorWithoutActiveProfileIsDeniedBeforeServiceRuns() {
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actor")
                .build();

        assertDeniedWith(
                PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE,
                () -> handler.handle(
                        new GetActiveProfileQuery(TARGET_USER_ENTRA_OID),
                        actor));

        verifyNoInteractions(service);
    }

    @Test
    void externalActorViewingOwnProfileReachesService() {
        stubProfileVisibility(UserType.EXTERNAL, TARGET_PROFILE_ID, TARGET_FIRM_ID);

        UserProfileDetailView expected = mock(UserProfileDetailView.class);

        AuthenticatedEntraUser actor = external(TARGET_PROFILE_ID, TARGET_FIRM_ID);

        GetActiveProfileQuery query = new GetActiveProfileQuery(TARGET_USER_ENTRA_OID);

        when(service.getActiveProfile(TARGET_USER_ENTRA_OID, TARGET_PROFILE_ID))
                .thenReturn(expected);

        assertThat(handler.handle(query, actor))
                .isSameAs(expected);

        verify(service).getActiveProfile(TARGET_USER_ENTRA_OID, TARGET_PROFILE_ID);
    }

    @Test
    void externalActorWithViewExternalPermissionInOwnFirmReachesService() {
        UUID profileId = UUID.randomUUID();
        stubProfileVisibility(UserType.EXTERNAL, profileId, TARGET_FIRM_ID);

        UserProfileDetailView expected = mock(UserProfileDetailView.class);

        AuthenticatedEntraUser actor = external(UUID.randomUUID(), TARGET_FIRM_ID, Permission.VIEW_EXTERNAL_USER);

        GetActiveProfileQuery query = new GetActiveProfileQuery(TARGET_USER_ENTRA_OID);

        when(service.getActiveProfile(TARGET_USER_ENTRA_OID, profileId))
                .thenReturn(expected);

        assertThat(handler.handle(query, actor))
                .isSameAs(expected);

        verify(service).getActiveProfile(TARGET_USER_ENTRA_OID, profileId);
    }

    @Test
    void externalActorWithoutViewExternalPermissionCannotViewColleague() {
        stubProfileVisibility(UserType.EXTERNAL, UUID.randomUUID(), TARGET_FIRM_ID);

        AuthenticatedEntraUser actor = external(UUID.randomUUID(), TARGET_FIRM_ID);

        assertDeniedWith(
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> handler.handle(
                        new GetActiveProfileQuery(TARGET_USER_ENTRA_OID),
                        actor));

        verifyNoInteractions(service);
    }

    @Test
    void externalActorInDifferentFirmIsDeniedBeforeServiceRuns() {
        stubProfileVisibility(UserType.EXTERNAL, UUID.randomUUID(), TARGET_FIRM_ID);

        AuthenticatedEntraUser actor = external(UUID.randomUUID(), UUID.randomUUID(), Permission.VIEW_EXTERNAL_USER);

        assertDeniedWith(
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> handler.handle(
                        new GetActiveProfileQuery(TARGET_USER_ENTRA_OID),
                        actor));

        verifyNoInteractions(service);
    }

    @Test
    void internalActorWithViewInternalPermissionReachesService() {
        UUID profileId = UUID.randomUUID();
        stubProfileVisibility(UserType.INTERNAL, profileId, null);

        UserProfileDetailView expected = mock(UserProfileDetailView.class);

        AuthenticatedEntraUser actor = internal(UUID.randomUUID(), Permission.VIEW_INTERNAL_USER);

        GetActiveProfileQuery query = new GetActiveProfileQuery(TARGET_USER_ENTRA_OID);

        when(service.getActiveProfile(TARGET_USER_ENTRA_OID, profileId))
                .thenReturn(expected);

        assertThat(handler.handle(query, actor))
                .isSameAs(expected);

        verify(service).getActiveProfile(TARGET_USER_ENTRA_OID, profileId);
    }

    @Test
    void internalActorWithoutViewInternalPermissionCannotViewInternalProfile() {
        stubProfileVisibility(UserType.INTERNAL, UUID.randomUUID(), null);

        AuthenticatedEntraUser actor = internal(UUID.randomUUID());

        assertDeniedWith(
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> handler.handle(
                        new GetActiveProfileQuery(TARGET_USER_ENTRA_OID),
                        actor));

        verifyNoInteractions(service);
    }

    @Test
    void internalActorWithViewExternalPermissionCanViewExternalProfile() {
        UUID profileId = UUID.randomUUID();
        stubProfileVisibility(UserType.EXTERNAL, profileId, TARGET_FIRM_ID);

        UserProfileDetailView expected = mock(UserProfileDetailView.class);

        AuthenticatedEntraUser actor = internal(UUID.randomUUID(), Permission.VIEW_EXTERNAL_USER);

        GetActiveProfileQuery query = new GetActiveProfileQuery(TARGET_USER_ENTRA_OID);

        when(service.getActiveProfile(TARGET_USER_ENTRA_OID, profileId))
                .thenReturn(expected);

        assertThat(handler.handle(query, actor))
                .isSameAs(expected);

        verify(service).getActiveProfile(TARGET_USER_ENTRA_OID, profileId);
    }

    @Test
    void missingTargetProfileIsDeniedConsistently() {
        // Repository returns empty list when profile doesn't exist for this Entra OID
        when(repository.findProfileVisibilityContexts(UUID.randomUUID().toString()))
                .thenReturn(java.util.List.of());

        AuthenticatedEntraUser actor = external(UUID.randomUUID(), TARGET_FIRM_ID, Permission.VIEW_EXTERNAL_USER);

        assertDeniedWith(
                PolicyDenialReason.TARGET_PROFILE_NOT_FOUND,
                () -> handler.handle(
                        new GetActiveProfileQuery(UUID.randomUUID().toString()),
                        actor));

        verifyNoInteractions(service);
    }

    @Test
    void permittedActorReceivesServiceResult() {
        stubProfileVisibility(UserType.EXTERNAL, TARGET_PROFILE_ID, TARGET_FIRM_ID);

        UserProfileDetailView expected = mock(UserProfileDetailView.class);

        AuthenticatedEntraUser actor = external(TARGET_PROFILE_ID, TARGET_FIRM_ID);

        GetActiveProfileQuery query = new GetActiveProfileQuery(TARGET_USER_ENTRA_OID);

        when(service.getActiveProfile(TARGET_USER_ENTRA_OID, TARGET_PROFILE_ID))
                .thenReturn(expected);

        UserProfileDetailView result = handler.handle(query, actor);

        assertThat(result).isSameAs(expected);
        verify(service).getActiveProfile(TARGET_USER_ENTRA_OID, TARGET_PROFILE_ID);
    }

    @Test
    void multiFirmUserCannotViewUnauthorizedFirmWhenSwitchingContexts() {
        UUID firmAprofileId = UUID.randomUUID();
        UUID firmBprofileId = UUID.randomUUID();
        UUID firmBid = UUID.randomUUID();

        // Target has two profiles in different firms
        when(repository.findProfileVisibilityContexts(TARGET_USER_ENTRA_OID))
                .thenReturn(java.util.List.of(
                        new ProfileVisibilityContext(true, firmAprofileId, UserType.EXTERNAL, TARGET_FIRM_ID),
                        new ProfileVisibilityContext(false, firmBprofileId, UserType.EXTERNAL, firmBid)));

        // Actor is authorized only for Firm A and has Firm A's profile as active
        AuthenticatedEntraUser actor = external(firmAprofileId, TARGET_FIRM_ID);

        GetActiveProfileQuery query = new GetActiveProfileQuery(TARGET_USER_ENTRA_OID);

        UserProfileDetailView expected = mock(UserProfileDetailView.class);
        when(service.getActiveProfile(TARGET_USER_ENTRA_OID, firmAprofileId))
                .thenReturn(expected);

        // Handler should fetch only the Firm A profile that was authorized
        UserProfileDetailView result = handler.handle(query, actor);

        assertThat(result).isSameAs(expected);
        // Verify service was called with the authorized Firm A profile ID, not any other profile
        verify(service).getActiveProfile(TARGET_USER_ENTRA_OID, firmAprofileId);
    }

    private static void assertDeniedWith(PolicyDenialReason reason, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AuthorizationDeniedException.class, denied ->
                        assertThat(denied.getAuthorizationResult()).isEqualTo(new PolicyAuthorizationDenial(reason)));
    }

    private void stubProfileVisibility(UserType type, UUID profileId, UUID firmId) {
        when(repository.findProfileVisibilityContexts(TARGET_USER_ENTRA_OID))
                .thenReturn(java.util.List.of(new ProfileVisibilityContext(true, profileId, type, firmId)));
    }

    private static AuthenticatedEntraUser external(UUID profileId, UUID firmId, Permission... permissions) {
        Set<AuthenticatedAppRole> roles = permissions.length == 0 ? Set.of()
                : Set.of(new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null, "Role",
                        true, null, Set.of(permissions)));
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(profileId)
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firmId(firmId)
                .appRoles(roles)
                .build();
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }

    private static AuthenticatedEntraUser internal(UUID profileId, Permission... permissions) {
        Set<AuthenticatedAppRole> roles = permissions.length == 0 ? Set.of()
                : Set.of(new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null, "Role",
                        true, null, Set.of(permissions)));
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(profileId)
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .appRoles(roles)
                .build();
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }
}
