package uk.gov.justice.laa.datauserapi.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

import uk.gov.justice.laa.datauserapi.application.query.dto.UserProfileAccessTarget;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess.GetUserProfileAppRolesQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess.GetUserProfileAppsQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess.GetUserProfileOfficesQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileAccessQueryService;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileVisibilityPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileAccessQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyAuthorizationDenial;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;

/**
 * Proves the profile-access queries are authorised by {@link CanViewProfilePolicy} through the real Spring
 * proxies: the repository is stubbed with the target's facts, so removing the policy would let every case pass.
 */
@SpringJUnitConfig(UserProfileAccessAuthorizationIntegrationTest.TestConfig.class)
class UserProfileAccessAuthorizationIntegrationTest {

    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID FIRM_ID = UUID.randomUUID();

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({RbacMethodSecurityConfig.class, GetUserProfileAppRolesHandler.class, GetUserProfileAppsHandler.class,
        GetUserProfileOfficesHandler.class, CanViewProfilePolicy.class,
        UserProfileVisibilityPolicyInputResolver.class})
    static class TestConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        UserProfileAccessQueryRepository userProfileAccessQueryRepository() {
            return mock(UserProfileAccessQueryRepository.class);
        }

        @Bean
        UserProfileAccessQueryService userProfileAccessQueryService() {
            return mock(UserProfileAccessQueryService.class);
        }
    }

    @Autowired
    private GetUserProfileAppRolesHandler appRolesHandler;
    @Autowired
    private GetUserProfileAppsHandler appsHandler;
    @Autowired
    private GetUserProfileOfficesHandler officesHandler;
    @Autowired
    private UserProfileAccessQueryRepository repository;
    @Autowired
    private UserProfileAccessQueryService service;

    @BeforeEach
    void setUp() {
        reset(repository, service);
    }

    @Test
    void externalActorInTheProfilesFirmReachesTheService() {
        stubTarget(UserType.EXTERNAL, FIRM_ID);
        AppViewList apps = new AppViewList(List.of());
        when(service.getApps(PROFILE_ID)).thenReturn(apps);

        assertThat(appsHandler.handle(new GetUserProfileAppsQuery(PROFILE_ID), external(FIRM_ID))).isSameAs(apps);
    }

    @Test
    void externalActorInAnotherFirmIsDeniedOnEveryEndpointBeforeTheServiceRuns() {
        stubTarget(UserType.EXTERNAL, FIRM_ID);
        AuthenticatedEntraUser otherFirm = external(UUID.randomUUID());

        assertDeniedWith(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> appsHandler.handle(new GetUserProfileAppsQuery(PROFILE_ID), otherFirm));
        assertDeniedWith(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> officesHandler.handle(new GetUserProfileOfficesQuery(PROFILE_ID), otherFirm));
        assertDeniedWith(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> appRolesHandler.handle(new GetUserProfileAppRolesQuery(PROFILE_ID, null, null), otherFirm));

        verifyNoInteractions(service);
    }

    @Test
    void missingProfileIsDeniedTheSameWayAsAnInvisibleOne() {
        when(repository.findAccessTargetById(PROFILE_ID)).thenReturn(Optional.empty());

        assertDeniedWith(PolicyDenialReason.TARGET_PROFILE_NOT_FOUND,
                () -> officesHandler.handle(new GetUserProfileOfficesQuery(PROFILE_ID), external(FIRM_ID)));
        verifyNoInteractions(service);
    }

    @Test
    void actorWithoutAnActiveProfileIsDenied() {
        stubTarget(UserType.EXTERNAL, FIRM_ID);
        AuthenticatedEntraUser noProfile = AuthenticatedEntraUser.builder().entraOid("oid").build();

        assertDeniedWith(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE,
                () -> appsHandler.handle(new GetUserProfileAppsQuery(PROFILE_ID), noProfile));
        verifyNoInteractions(service);
    }

    @Test
    void permittedActorReceivesTheServiceResultWithTheRequestedFilters() {
        stubTarget(UserType.EXTERNAL, FIRM_ID);
        UUID appId = UUID.randomUUID();
        AppRoleViewList roles = new AppRoleViewList(List.of());
        when(service.getAppRoles(PROFILE_ID, appId, null)).thenReturn(roles);

        assertThat(appRolesHandler.handle(new GetUserProfileAppRolesQuery(PROFILE_ID, appId, null), external(FIRM_ID)))
                .isSameAs(roles);
        verify(service).getAppRoles(PROFILE_ID, appId, null);
    }

    private static void assertDeniedWith(PolicyDenialReason reason, ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AuthorizationDeniedException.class, denied ->
                        assertThat(denied.getAuthorizationResult()).isEqualTo(new PolicyAuthorizationDenial(reason)));
    }

    private void stubTarget(UserType type, UUID firmId) {
        when(repository.findAccessTargetById(PROFILE_ID))
                .thenReturn(Optional.of(new UserProfileAccessTarget(type, firmId, false)));
    }

    private static AuthenticatedEntraUser external(UUID firmId) {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firmId(firmId)
                .appRoles(Set.of())
                .build();
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }
}
