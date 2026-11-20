package uk.gov.justice.laa.datauserapi.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
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

import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppRoleByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppRolesByAppQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppsQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetRoleAssignmentRestrictionsQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AppCatalogueQueryService;
import uk.gov.justice.laa.datauserapi.application.query.service.RoleAssignmentRestrictionQueryService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;

/**
 * Proves every catalogue query requires {@code VIEW_LAA_APP_METADATA} through the real Spring proxies: the services
 * are mocks, so removing the requirement from a query would let the denied cases reach them.
 */
@SpringJUnitConfig(AppCatalogueAuthorizationIntegrationTest.TestConfig.class)
class AppCatalogueAuthorizationIntegrationTest {

    private static final UUID ID = UUID.randomUUID();

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({RbacMethodSecurityConfig.class, GetAppsHandler.class, GetAppByIdHandler.class,
        GetAppRolesByAppHandler.class, GetAppRoleByIdHandler.class, GetRoleAssignmentRestrictionsHandler.class})
    static class TestConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        AppCatalogueQueryService appCatalogueQueryService() {
            return mock(AppCatalogueQueryService.class);
        }

        @Bean
        RoleAssignmentRestrictionQueryService roleAssignmentRestrictionQueryService() {
            return mock(RoleAssignmentRestrictionQueryService.class);
        }
    }

    @Autowired
    private GetAppsHandler appsHandler;
    @Autowired
    private GetAppByIdHandler appByIdHandler;
    @Autowired
    private GetAppRolesByAppHandler appRolesHandler;
    @Autowired
    private GetAppRoleByIdHandler appRoleByIdHandler;
    @Autowired
    private GetRoleAssignmentRestrictionsHandler restrictionsHandler;
    @Autowired
    private AppCatalogueQueryService service;
    @Autowired
    private RoleAssignmentRestrictionQueryService restrictionService;

    @BeforeEach
    void setUp() {
        reset(service, restrictionService);
    }

    @Test
    void actorWithTheMetadataPermissionReachesTheService() {
        AppViewList apps = new AppViewList(List.of());
        when(service.getApps()).thenReturn(apps);

        assertThat(appsHandler.handle(new GetAppsQuery(), actor(Permission.VIEW_LAA_APP_METADATA))).isSameAs(apps);
    }

    @Test
    void actorWithoutTheMetadataPermissionIsDeniedOnEveryEndpointBeforeTheServiceRuns() {
        AuthenticatedEntraUser other = actor(Permission.VIEW_EXTERNAL_USER, Permission.EDIT_LAA_APP_METADATA);

        assertDenied(() -> appsHandler.handle(new GetAppsQuery(), other));
        assertDenied(() -> appByIdHandler.handle(new GetAppByIdQuery(ID), other));
        assertDenied(() -> appRolesHandler.handle(new GetAppRolesByAppQuery(ID, UserType.INTERNAL), other));
        assertDenied(() -> appRoleByIdHandler.handle(new GetAppRoleByIdQuery(ID), other));
        assertDenied(() -> restrictionsHandler.handle(new GetRoleAssignmentRestrictionsQuery(ID, 0, 20), other));
        assertDenied(() -> appsHandler.handle(new GetAppsQuery(), AuthenticatedEntraUser.builder()
                .entraOid("oid").build()));

        verifyNoInteractions(service, restrictionService);
    }

    @Test
    void permittedActorReachesEveryHandlersService() {
        AuthenticatedEntraUser permitted = actor(Permission.VIEW_LAA_APP_METADATA);

        appByIdHandler.handle(new GetAppByIdQuery(ID), permitted);
        appRolesHandler.handle(new GetAppRolesByAppQuery(ID, UserType.EXTERNAL), permitted);
        appRoleByIdHandler.handle(new GetAppRoleByIdQuery(ID), permitted);
        restrictionsHandler.handle(new GetRoleAssignmentRestrictionsQuery(ID, 1, 5), permitted);

        verify(service).getApp(ID);
        verify(service).getAppRoles(ID, UserType.EXTERNAL);
        verify(service).getAppRole(ID);
        verify(restrictionService).getRestrictions(ID, 1, 5);
    }

    private static void assertDenied(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(AuthorizationDeniedException.class);
    }

    private static AuthenticatedEntraUser actor(Permission... permissions) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null,
                null, "Role", true, null, Set.of(permissions));
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .appRoles(Set.of(role))
                .build();
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }
}
