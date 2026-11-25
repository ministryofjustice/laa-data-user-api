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
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanExportAuditUsersPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserExportAuthorizationPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserExportService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves {@link CanExportAuditUsersPolicy} through the real Spring-proxied
 * {@link ExportAuditUsersHandler}, so each rule's removal would fail its matching test.
 */
@SpringJUnitConfig(ExportAuditUsersAuthorizationIntegrationTest.TestConfig.class)
class ExportAuditUsersAuthorizationIntegrationTest {

    private static final String ACTOR_FIRM_ID = "FIRM001";
    private static final String OTHER_FIRM_ID = "FIRM002";

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({
        RbacMethodSecurityConfig.class,
        ExportAuditUsersHandler.class,
        CanExportAuditUsersPolicy.class,
        AuditUserExportAuthorizationPolicyInputResolver.class
    })
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        AuditUserExportService auditUserExportService() {
            return mock(AuditUserExportService.class);
        }
    }

    @Autowired
    private ExportAuditUsersHandler handler;

    @Autowired
    private AuditUserExportService service;

    @BeforeEach
    void setUp() {
        reset(service);
    }

    @Test
    void actorWithoutActiveProfileIsDeniedBeforeServiceRuns() {
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid("actor").build();

        assertDeniedWith(
                PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE,
                () -> handler.handle(new ExportAuditUsersQuery(internalOnlyCriteria()), actor));

        verifyNoInteractions(service);
    }

    @Test
    void actorWithoutExportPermissionIsDenied() {
        AuthenticatedEntraUser actor = internal(Permission.VIEW_INTERNAL_USER);

        assertDeniedWith(
                PolicyDenialReason.ACTOR_NO_EXPORT_PERMISSION,
                () -> handler.handle(new ExportAuditUsersQuery(internalOnlyCriteria()), actor));

        verifyNoInteractions(service);
    }

    @Test
    void internalOnlyExportWithoutViewInternalPermissionIsDenied() {
        AuthenticatedEntraUser actor = internal(Permission.EXPORT_AUDIT_DATA);

        assertDeniedWith(
                PolicyDenialReason.ACTOR_NO_VIEW_PERMISSION,
                () -> handler.handle(new ExportAuditUsersQuery(internalOnlyCriteria()), actor));

        verifyNoInteractions(service);
    }

    @Test
    void internalOnlyExportWithPermissionsReachesService() {
        AuthenticatedEntraUser actor = internal(Permission.EXPORT_AUDIT_DATA, Permission.VIEW_INTERNAL_USER);
        ExportAuditUsersQuery query = new ExportAuditUsersQuery(internalOnlyCriteria());
        List<AuditUserExportRow> expected = List.of();
        when(service.export(query)).thenReturn(expected);

        assertThat(handler.handle(query, actor)).isSameAs(expected);

        verify(service).export(query);
    }

    @Test
    void firmExportWithoutViewExternalPermissionIsDenied() {
        AuthenticatedEntraUser actor = external(ACTOR_FIRM_ID, Permission.EXPORT_AUDIT_DATA);

        assertDeniedWith(
                PolicyDenialReason.ACTOR_NO_VIEW_PERMISSION,
                () -> handler.handle(new ExportAuditUsersQuery(firmCriteria(ACTOR_FIRM_ID)), actor));

        verifyNoInteractions(service);
    }

    @Test
    void externalActorRequestingOwnFirmReachesService() {
        AuthenticatedEntraUser actor =
                external(ACTOR_FIRM_ID, Permission.EXPORT_AUDIT_DATA, Permission.VIEW_EXTERNAL_USER);
        ExportAuditUsersQuery query = new ExportAuditUsersQuery(firmCriteria(ACTOR_FIRM_ID));
        List<AuditUserExportRow> expected = List.of();
        when(service.export(query)).thenReturn(expected);

        assertThat(handler.handle(query, actor)).isSameAs(expected);

        verify(service).export(query);
    }

    @Test
    void externalActorRequestingAnotherFirmIsDenied() {
        AuthenticatedEntraUser actor =
                external(ACTOR_FIRM_ID, Permission.EXPORT_AUDIT_DATA, Permission.VIEW_EXTERNAL_USER);

        assertDeniedWith(
                PolicyDenialReason.TARGET_FIRM_OUTSIDE_ACTOR_FIRM,
                () -> handler.handle(new ExportAuditUsersQuery(firmCriteria(OTHER_FIRM_ID)), actor));

        verifyNoInteractions(service);
    }

    @Test
    void internalActorRequestingFirmExportReachesService() {
        AuthenticatedEntraUser actor = internal(Permission.EXPORT_AUDIT_DATA, Permission.VIEW_EXTERNAL_USER);
        ExportAuditUsersQuery query = new ExportAuditUsersQuery(firmCriteria(OTHER_FIRM_ID));
        List<AuditUserExportRow> expected = List.of();
        when(service.export(query)).thenReturn(expected);

        assertThat(handler.handle(query, actor)).isSameAs(expected);

        verify(service).export(query);
    }

    private static void assertDeniedWith(PolicyDenialReason reason, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AuthorizationDeniedException.class, denied ->
                        assertThat(denied.getAuthorizationResult()).isEqualTo(new PolicyAuthorizationDenial(reason)));
    }

    private static AuditUserExportCriteria internalOnlyCriteria() {
        return new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);
    }

    private static AuditUserExportCriteria firmCriteria(String firmId) {
        return new AuditUserExportCriteria(UserType.EXTERNAL, null, firmId, null, null, null, null);
    }

    private static AuthenticatedEntraUser internal(Permission... permissions) {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .appRoles(appRoles(permissions))
                .build();
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }

    private static AuthenticatedEntraUser external(String firmCode, Permission... permissions) {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firmCode(firmCode)
                .appRoles(appRoles(permissions))
                .build();
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }

    private static Set<AuthenticatedAppRole> appRoles(Permission... permissions) {
        if (permissions.length == 0) {
            return Set.of();
        }
        return Set.of(new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null, "Role",
                true, null, Set.of(permissions)));
    }
}
