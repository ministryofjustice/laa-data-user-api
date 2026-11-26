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
import uk.gov.justice.laa.datauserapi.application.query.dto.ExportBody;
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

import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void unconstrainedRequestBypassingControllerValidationIsDeniedByPolicy() {
        AuthenticatedEntraUser actor = internal(
                Permission.EXPORT_AUDIT_DATA, Permission.VIEW_INTERNAL_USER, Permission.VIEW_EXTERNAL_USER);
        AuditUserExportCriteria unconstrainedCriteria =
                new AuditUserExportCriteria(null, null, null, null, null, null, null);

        assertDeniedWith(
                PolicyDenialReason.EXPORT_SCOPE_NOT_CONSTRAINED,
                () -> handler.handle(new ExportAuditUsersQuery(unconstrainedCriteria), actor));

        verifyNoInteractions(service);
    }

    @Test
    void conflictingRequestBypassingControllerValidationIsDeniedByPolicy() {
        AuthenticatedEntraUser actor = internal(
                Permission.EXPORT_AUDIT_DATA, Permission.VIEW_INTERNAL_USER, Permission.VIEW_EXTERNAL_USER);
        AuditUserExportCriteria conflictingCriteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, ACTOR_FIRM_ID, null, null, null, null);

        assertDeniedWith(
                PolicyDenialReason.EXPORT_SCOPE_NOT_CONSTRAINED,
                () -> handler.handle(new ExportAuditUsersQuery(conflictingCriteria), actor));

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
    void internalOnlyExportWithPermissionsReachesService() throws Exception {
        AuthenticatedEntraUser actor = internal(Permission.EXPORT_AUDIT_DATA, Permission.VIEW_INTERNAL_USER);
        AuditUserExportCriteria criteria = internalOnlyCriteria();
        ExportAuditUsersQuery query = new ExportAuditUsersQuery(criteria);

        ExportBody body = handler.handle(query, actor);
        assertThat(body).isNotNull();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);

        verify(service).export(criteria, out);
    }

    @Test
    void externalActorWithViewInternalPermissionIsDeniedInternalOnlyExport() {
        AuthenticatedEntraUser actor = external(
                ACTOR_FIRM_ID, Permission.EXPORT_AUDIT_DATA, Permission.VIEW_INTERNAL_USER);

        assertDeniedWith(
                PolicyDenialReason.ACTOR_NO_VIEW_PERMISSION,
                () -> handler.handle(new ExportAuditUsersQuery(internalOnlyCriteria()), actor));

        verifyNoInteractions(service);
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
    void externalActorRequestingOwnFirmReachesService() throws Exception {
        AuthenticatedEntraUser actor =
                external(ACTOR_FIRM_ID, Permission.EXPORT_AUDIT_DATA, Permission.VIEW_EXTERNAL_USER);
        AuditUserExportCriteria criteria = firmCriteria(ACTOR_FIRM_ID);
        ExportAuditUsersQuery query = new ExportAuditUsersQuery(criteria);

        ExportBody body = handler.handle(query, actor);
        assertThat(body).isNotNull();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);

        verify(service).export(criteria, out);
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
    void internalActorRequestingFirmExportReachesService() throws Exception {
        AuthenticatedEntraUser actor = internal(Permission.EXPORT_AUDIT_DATA, Permission.VIEW_EXTERNAL_USER);
        AuditUserExportCriteria criteria = firmCriteria(OTHER_FIRM_ID);
        ExportAuditUsersQuery query = new ExportAuditUsersQuery(criteria);

        ExportBody body = handler.handle(query, actor);
        assertThat(body).isNotNull();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);

        verify(service).export(criteria, out);
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
