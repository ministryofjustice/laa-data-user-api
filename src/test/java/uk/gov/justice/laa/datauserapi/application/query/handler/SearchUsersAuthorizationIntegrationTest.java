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
import uk.gov.justice.laa.datauserapi.application.query.policy.CanSearchUsersPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserSearchAuthorizationPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.query.service.UserSearchService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryPage;
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

@SpringJUnitConfig(SearchUsersAuthorizationIntegrationTest.TestConfig.class)
public class SearchUsersAuthorizationIntegrationTest {

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({
        RbacMethodSecurityConfig.class,
        SearchUsersHandler.class,
        CanSearchUsersPolicy.class,
        UserSearchAuthorizationPolicyInputResolver.class
    })
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        UserSearchService userSearchService() {
            return mock(UserSearchService.class);
        }
    }

    @Autowired
    private SearchUsersHandler handler;

    @Autowired
    private UserSearchService service;

    @BeforeEach
    void setUp() {
        reset(service, service);
    }

    @Test
    void actorWithoutActiveProfileIsDeniedBeforeServiceRuns() {

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actor")
                .build();

        assertDeniedWith(
                PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE,
                () -> handler.handle(
                        new SearchUsersQuery(mock(UserSearchCriteria.class)),
                        actor));


        verifyNoInteractions(service);
    }

    @Test
    void actorWithActiveProfileReachesService() {

        UserAccountSummaryPage expected = mock(UserAccountSummaryPage.class);

        AuthenticatedEntraUser actor =
                external(UUID.randomUUID(), UUID.randomUUID());

        SearchUsersQuery query =
                new SearchUsersQuery(mock(UserSearchCriteria.class));

        when(service.search(query, actor))
                .thenReturn(expected);

        assertThat(handler.handle(query, actor))
                .isSameAs(expected);

        verify(service).search(query, actor);
    }

    private static void assertDeniedWith(PolicyDenialReason reason, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AuthorizationDeniedException.class, denied ->
                        assertThat(denied.getAuthorizationResult()).isEqualTo(new PolicyAuthorizationDenial(reason)));
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
}
