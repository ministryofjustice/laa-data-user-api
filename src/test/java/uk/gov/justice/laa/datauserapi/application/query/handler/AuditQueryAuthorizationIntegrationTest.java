package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.assertj.core.api.Assertions;
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
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAccountPolicy;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAuditUserPolicy;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewDeletedUsersAuditPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.DeletedUsersAuditQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditUserAccessRepository;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserDetailService;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserVisibilityPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.query.service.DeletedUsersAuditService;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountVisibilityContextFactory;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditAccountRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditProfileRow;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditDeletedUsersCriteria;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyAuthorizationDenial;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Proves the audit endpoints' authorisation through real Spring-proxied handlers. */
@SpringJUnitConfig(AuditQueryAuthorizationIntegrationTest.TestConfig.class)
class AuditQueryAuthorizationIntegrationTest {

    private static final String TARGET_OID = "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d";
    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final AuditDeletedUsersCriteria CRITERIA = new AuditDeletedUsersCriteria(0, 20, null);

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({
        RbacMethodSecurityConfig.class,
        AuditUserDetailHandler.class,
        DeletedUsersAuditHandler.class,
        CanViewAccountPolicy.class,
        CanViewAuditUserPolicy.class,
        CanViewDeletedUsersAuditPolicy.class,
        AuditUserVisibilityPolicyInputResolver.class,
        UserAccountVisibilityContextFactory.class
    })
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        AuditUserDetailService auditUserDetailService() {
            return mock(AuditUserDetailService.class);
        }

        @Bean
        DeletedUsersAuditService deletedUsersAuditService() {
            return mock(DeletedUsersAuditService.class);
        }

        @Bean
        AuditUserAccessRepository auditUserAccessRepository() {
            return mock(AuditUserAccessRepository.class);
        }
    }

    @Autowired
    private AuditUserDetailHandler detailHandler;
    @Autowired
    private DeletedUsersAuditHandler deletedHandler;
    @Autowired
    private AuditUserDetailService detailService;
    @Autowired
    private DeletedUsersAuditService deletedService;
    @Autowired
    private AuditUserAccessRepository accessRepository;

    @BeforeEach
    void setUp() {
        reset(detailService, deletedService, accessRepository);
    }

    @Test
    void detailRequiresViewAuditTablePermissionBeforeTargetIsLoaded() {
        AuthenticatedEntraUser actor = actor(UserType.INTERNAL, Permission.VIEW_INTERNAL_USER,
                Permission.VIEW_EXTERNAL_USER);

        assertThatThrownBy(() -> detailHandler.handle(new AuditUserDetailQuery(TARGET_OID), actor))
                .isInstanceOf(AuthorizationDeniedException.class);

        verifyNoInteractions(accessRepository, detailService);
    }

    @Test
    void detailForUnknownTargetIsForbiddenLikeAnInaccessibleOne() {
        when(accessRepository.findAccount(TARGET_OID)).thenReturn(Optional.empty());

        assertDeniedWith(PolicyDenialReason.TARGET_PROFILE_NOT_FOUND, () -> detailHandler.handle(
                new AuditUserDetailQuery(TARGET_OID),
                actor(UserType.INTERNAL, Permission.VIEW_AUDIT_TABLE, Permission.VIEW_EXTERNAL_USER)));

        verifyNoInteractions(detailService);
    }

    @Test
    void detailForExternalTargetIsDeniedWithoutViewExternalUser() {
        stubTarget(UserType.EXTERNAL);

        assertDeniedWith(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE, () -> detailHandler.handle(
                new AuditUserDetailQuery(TARGET_OID),
                actor(UserType.INTERNAL, Permission.VIEW_AUDIT_TABLE, Permission.VIEW_INTERNAL_USER)));

        verifyNoInteractions(detailService);
    }

    @Test
    void detailForInternalTargetIsDeniedWithoutViewInternalUser() {
        stubTarget(UserType.INTERNAL);

        assertDeniedWith(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE, () -> detailHandler.handle(
                new AuditUserDetailQuery(TARGET_OID),
                actor(UserType.INTERNAL, Permission.VIEW_AUDIT_TABLE, Permission.VIEW_EXTERNAL_USER)));

        verifyNoInteractions(detailService);
    }

    @Test
    void detailForVisibleTargetReachesService() {
        stubTarget(UserType.EXTERNAL);
        AuthenticatedEntraUser actor = actor(UserType.INTERNAL, Permission.VIEW_AUDIT_TABLE,
                Permission.VIEW_EXTERNAL_USER);
        AuditUserDetailQuery query = new AuditUserDetailQuery(TARGET_OID);
        AuditUserDetailView expected = mock(AuditUserDetailView.class);
        when(detailService.getDetail(query, actor)).thenReturn(expected);

        assertThat(detailHandler.handle(query, actor)).isSameAs(expected);
    }

    @Test
    void deletedUsersRequireViewAuditTablePermission() {
        AuthenticatedEntraUser actor = actor(UserType.INTERNAL, Permission.VIEW_EXTERNAL_USER);

        assertThatThrownBy(() -> deletedHandler.handle(new DeletedUsersAuditQuery(CRITERIA), actor))
                .isInstanceOf(AuthorizationDeniedException.class);

        verifyNoInteractions(deletedService);
    }

    @Test
    void deletedUsersAreForbiddenToExternalActors() {
        assertDeniedWith(PolicyDenialReason.ACTOR_NOT_ACTIVE_INTERNAL_USER, () -> deletedHandler.handle(
                new DeletedUsersAuditQuery(CRITERIA),
                actor(UserType.EXTERNAL, Permission.VIEW_AUDIT_TABLE, Permission.VIEW_EXTERNAL_USER)));

        verifyNoInteractions(deletedService);
    }

    @Test
    void deletedUsersReachServiceForInternalActorWithPermission() {
        AuthenticatedEntraUser actor = actor(UserType.INTERNAL, Permission.VIEW_AUDIT_TABLE);
        DeletedUsersAuditQuery query = new DeletedUsersAuditQuery(CRITERIA);
        DeletedUserAuditPage expected = mock(DeletedUserAuditPage.class);
        when(deletedService.getDeletedUsers(query, actor)).thenReturn(expected);

        assertThat(deletedHandler.handle(query, actor)).isSameAs(expected);
    }

    private void stubTarget(UserType targetType) {
        when(accessRepository.findAccount(TARGET_OID)).thenReturn(Optional.of(new AuditAccountRow(
                TARGET_OID, "a@b.test", "First", "Last", UserAccountStatus.ACTIVE, false, false, null, null, null,
                null)));
        when(accessRepository.findProfileRows(TARGET_OID)).thenReturn(List.of(new AuditProfileRow(
                UUID.randomUUID(), true, targetType, targetType == UserType.EXTERNAL ? FIRM_ID : null)));
    }

    private static AuthenticatedEntraUser actor(UserType userType, Permission... permissions) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false,
                null, null, "Role", true, null, Set.of(permissions));
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(userType)
                .firmId(userType == UserType.EXTERNAL ? UUID.randomUUID() : null)
                .appRoles(Set.of(role))
                .build();
        return AuthenticatedEntraUser.builder()
                .entraOid("actor-oid")
                .active(true)
                .activeProfile(profile)
                .build();
    }

    private static void assertDeniedWith(PolicyDenialReason reason, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AuthorizationDeniedException.class, denied ->
                        Assertions.assertThat(denied.getAuthorizationResult())
                                .isEqualTo(new PolicyAuthorizationDenial(reason)));
    }
}
