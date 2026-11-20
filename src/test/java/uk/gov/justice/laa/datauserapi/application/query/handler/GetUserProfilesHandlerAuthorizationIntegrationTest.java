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
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfileListPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileListVisibilityPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyAuthorizationDenial;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(GetUserProfilesHandlerAuthorizationIntegrationTest.TestConfig.class)
class GetUserProfilesHandlerAuthorizationIntegrationTest {

    private static final UUID TARGET_PROFILE_ID = UUID.randomUUID();
    private static final UUID TARGET_FIRM_ID = UUID.randomUUID();
    private static final String TARGET_USER_ENTRA_OID = UUID.randomUUID().toString();

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({
        RbacMethodSecurityConfig.class,
        GetUserProfilesHandler.class,
        CanViewProfileListPolicy.class,
        UserProfileListVisibilityPolicyInputResolver.class
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
        UserProfileDetailsService userProfileDetailsService(UserProfileQueryRepository repository) {
            return new UserProfileDetailsService(
                    repository,
                    mock(),
                    mock());
        }
    }

    @Autowired
    private GetUserProfilesHandler handler;

    @Autowired
    private UserProfileQueryRepository repository;

    @BeforeEach
    void setUp() {
        reset(repository);
    }

    @Test
    void actorWithoutActiveProfileIsDeniedBeforeServiceRuns() {
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actor")
                .build();

        assertDeniedWith(
                PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE,
                () -> handler.handle(
                        new GetUserProfilesQuery(TARGET_USER_ENTRA_OID),
                        actor));
    }

    @Test
    void externalActorViewingOwnProfileReachesService() {
        stubProfileVisibility(UserType.EXTERNAL, TARGET_PROFILE_ID, TARGET_FIRM_ID);

        AuthenticatedEntraUser actor = external(TARGET_PROFILE_ID, TARGET_FIRM_ID);

        GetUserProfilesQuery query = new GetUserProfilesQuery(TARGET_USER_ENTRA_OID);

        List<UserProfileSummaryView> result = handler.handle(query, actor);

        assertThat(result).isNotNull();
    }

    @Test
    void externalActorWithViewExternalPermissionInOwnFirmReachesService() {
        stubProfileVisibility(UserType.EXTERNAL, UUID.randomUUID(), TARGET_FIRM_ID);

        AuthenticatedEntraUser actor = external(UUID.randomUUID(), TARGET_FIRM_ID, Permission.VIEW_EXTERNAL_USER);

        GetUserProfilesQuery query = new GetUserProfilesQuery(TARGET_USER_ENTRA_OID);

        List<UserProfileSummaryView> result = handler.handle(query, actor);

        assertThat(result).isNotNull();
    }

    @Test
    void externalActorWithoutViewExternalPermissionCannotViewColleague() {
        stubProfileVisibility(UserType.EXTERNAL, UUID.randomUUID(), TARGET_FIRM_ID);

        AuthenticatedEntraUser actor = external(UUID.randomUUID(), TARGET_FIRM_ID);

        assertDeniedWith(
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> handler.handle(
                        new GetUserProfilesQuery(TARGET_USER_ENTRA_OID),
                        actor));
    }

    @Test
    void externalActorInDifferentFirmIsDeniedBeforeServiceRuns() {
        stubProfileVisibility(UserType.EXTERNAL, UUID.randomUUID(), TARGET_FIRM_ID);

        AuthenticatedEntraUser actor = external(UUID.randomUUID(), UUID.randomUUID(), Permission.VIEW_EXTERNAL_USER);

        assertDeniedWith(
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> handler.handle(
                        new GetUserProfilesQuery(TARGET_USER_ENTRA_OID),
                        actor));
    }

    @Test
    void internalActorWithViewInternalPermissionReachesService() {
        stubProfileVisibility(UserType.INTERNAL, UUID.randomUUID(), null);

        AuthenticatedEntraUser actor = internal(UUID.randomUUID(), Permission.VIEW_INTERNAL_USER);

        GetUserProfilesQuery query = new GetUserProfilesQuery(TARGET_USER_ENTRA_OID);

        List<UserProfileSummaryView> result = handler.handle(query, actor);

        assertThat(result).isNotNull();
    }

    @Test
    void internalActorWithoutViewInternalPermissionCannotViewInternalProfile() {
        stubProfileVisibility(UserType.INTERNAL, UUID.randomUUID(), null);

        AuthenticatedEntraUser actor = internal(UUID.randomUUID());

        assertDeniedWith(
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> handler.handle(
                        new GetUserProfilesQuery(TARGET_USER_ENTRA_OID),
                        actor));
    }

    @Test
    void internalActorWithViewExternalPermissionCanViewExternalProfile() {
        stubProfileVisibility(UserType.EXTERNAL, UUID.randomUUID(), TARGET_FIRM_ID);

        AuthenticatedEntraUser actor = internal(UUID.randomUUID(), Permission.VIEW_EXTERNAL_USER);

        GetUserProfilesQuery query = new GetUserProfilesQuery(TARGET_USER_ENTRA_OID);

        List<UserProfileSummaryView> result = handler.handle(query, actor);

        assertThat(result).isNotNull();
    }

    @Test
    void missingTargetProfileIsDeniedConsistently() {
        String missingUserOid = UUID.randomUUID().toString();
        when(repository.findProfileVisibilityContexts(missingUserOid))
                .thenReturn(java.util.List.of());

        AuthenticatedEntraUser actor = external(UUID.randomUUID(), TARGET_FIRM_ID, Permission.VIEW_EXTERNAL_USER);

        assertDeniedWith(
                PolicyDenialReason.TARGET_PROFILE_NOT_FOUND,
                () -> handler.handle(
                        new GetUserProfilesQuery(missingUserOid),
                        actor));
    }

    @Test
    void multiProfileExternalUserActiveInFirmOneSeesOnlyFirmOneProfiles() {
        UUID firmOneId = UUID.randomUUID();
        UUID firmTwoId = UUID.randomUUID();
        UUID profileOneId = UUID.randomUUID();
        UUID profileTwoId = UUID.randomUUID();

        when(repository.findProfileVisibilityContexts(TARGET_USER_ENTRA_OID))
                .thenReturn(List.of(
                        new ProfileVisibilityContext(true, profileOneId, UserType.EXTERNAL, firmOneId),
                        new ProfileVisibilityContext(true, profileTwoId, UserType.EXTERNAL, firmTwoId)
                ));

        when(repository.findAllProfilesByUserEntraObjectId(TARGET_USER_ENTRA_OID))
                .thenReturn(java.util.Optional.of(List.of(
                        createProfileSummary(profileOneId, UserType.EXTERNAL, firmOneId.toString()),
                        createProfileSummary(profileTwoId, UserType.EXTERNAL, firmTwoId.toString())
                )));

        AuthenticatedEntraUser actorActiveFirmOne = external(profileOneId, firmOneId, Permission.VIEW_EXTERNAL_USER);

        GetUserProfilesQuery query = new GetUserProfilesQuery(TARGET_USER_ENTRA_OID);

        List<UserProfileSummaryView> result = handler.handle(query, actorActiveFirmOne);

        assertThat(result).hasSize(1).extracting(UserProfileSummaryView::userProfileId).containsExactly(profileOneId);
    }

    @Test
    void multiProfileExternalUserSwitchingToFirmTwoSeesOnlyFirmTwoProfiles() {
        UUID firmOneId = UUID.randomUUID();
        UUID firmTwoId = UUID.randomUUID();
        UUID profileOneId = UUID.randomUUID();
        UUID profileTwoId = UUID.randomUUID();

        when(repository.findProfileVisibilityContexts(TARGET_USER_ENTRA_OID))
                .thenReturn(List.of(
                        new ProfileVisibilityContext(true, profileOneId, UserType.EXTERNAL, firmOneId),
                        new ProfileVisibilityContext(true, profileTwoId, UserType.EXTERNAL, firmTwoId)
                ));

        when(repository.findAllProfilesByUserEntraObjectId(TARGET_USER_ENTRA_OID))
                .thenReturn(java.util.Optional.of(List.of(
                        createProfileSummary(profileOneId, UserType.EXTERNAL, firmOneId.toString()),
                        createProfileSummary(profileTwoId, UserType.EXTERNAL, firmTwoId.toString())
                )));

        AuthenticatedEntraUser actorActiveFirmTwo = external(profileTwoId, firmTwoId, Permission.VIEW_EXTERNAL_USER);

        GetUserProfilesQuery query = new GetUserProfilesQuery(TARGET_USER_ENTRA_OID);

        List<UserProfileSummaryView> result = handler.handle(query, actorActiveFirmTwo);

        assertThat(result).hasSize(1).extracting(UserProfileSummaryView::userProfileId).containsExactly(profileTwoId);
    }

    @Test
    void multiProfileExternalUserWithoutViewExternalPermissionSeesOnlyOwnProfile() {
        UUID firmOneId = UUID.randomUUID();
        UUID firmTwoId = UUID.randomUUID();
        UUID profileOneId = UUID.randomUUID();
        UUID profileTwoId = UUID.randomUUID();
        UUID sameColleagueProfileId = UUID.randomUUID();

        when(repository.findProfileVisibilityContexts(TARGET_USER_ENTRA_OID))
                .thenReturn(List.of(
                        new ProfileVisibilityContext(true, profileOneId, UserType.EXTERNAL, firmOneId),
                        new ProfileVisibilityContext(true, profileTwoId, UserType.EXTERNAL, firmTwoId),
                        new ProfileVisibilityContext(true, sameColleagueProfileId, UserType.EXTERNAL, firmOneId)
                ));

        when(repository.findAllProfilesByUserEntraObjectId(TARGET_USER_ENTRA_OID))
                .thenReturn(java.util.Optional.of(List.of(
                        createProfileSummary(profileOneId, UserType.EXTERNAL, firmOneId.toString()),
                        createProfileSummary(profileTwoId, UserType.EXTERNAL, firmTwoId.toString()),
                        createProfileSummary(sameColleagueProfileId, UserType.EXTERNAL, firmOneId.toString())
                )));

        AuthenticatedEntraUser actorActiveFirmOne = external(profileOneId, firmOneId);

        GetUserProfilesQuery query = new GetUserProfilesQuery(TARGET_USER_ENTRA_OID);

        List<UserProfileSummaryView> result = handler.handle(query, actorActiveFirmOne);

        assertThat(result).hasSize(1).extracting(UserProfileSummaryView::userProfileId).containsExactly(profileOneId);
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

    private static UserProfileSummaryView createProfileSummary(UUID profileId, UserType userType, String firmId) {
        return new UserProfileSummaryView(
                profileId,
                true,
                userType,
                "test@example.com",
                "Test User",
                "Test Firm",
                firmId,
                false,
                null,
                null,
                false
        );
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
