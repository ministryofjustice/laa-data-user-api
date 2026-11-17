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
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountDetailsService;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountVisibilityPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.FirmDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyAuthorizationDenial;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(GetUserAccountAuthorizationIntegrationTest.TestConfig.class)
class GetUserAccountAuthorizationIntegrationTest {

    private static final String TARGET_OID =
            "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d";

    private static final UUID FIRM_ID = UUID.randomUUID();

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({
        RbacMethodSecurityConfig.class,
        GetUserAccountHandler.class,
        CanViewAccountPolicy.class,
        UserAccountVisibilityPolicyInputResolver.class
    })
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        UserAccountDetailsService userAccountDetailsService() {
            return mock(UserAccountDetailsService.class);
        }

        @Bean
        UserAccountQueryService userAccountQueryService() {
            return mock(UserAccountQueryService.class);
        }
    }

    @Autowired
    private GetUserAccountHandler handler;

    @Autowired
    private UserAccountDetailsService service;

    @Autowired
    private UserAccountQueryService queryService;

    @BeforeEach
    void setUp() {
        reset(service, queryService);
    }

    @Test
    void actorWithoutActiveProfileIsDeniedBeforeServiceRuns() {

        stubTargetAccount();

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actor-oid")
                .build();

        assertDeniedWith(
                PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE,
                () -> handler.handle(
                        new GetUserAccountQuery(TARGET_OID),
                        actor));

        verifyNoInteractions(service);
    }

    @Test
    void missingAccountIsDeniedBeforeServiceRuns() {

        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_OID)).thenReturn(null);

        assertDeniedWith(PolicyDenialReason.TARGET_PROFILE_NOT_FOUND,
                () -> handler.handle(
                        new GetUserAccountQuery(TARGET_OID),
                        external(UUID.randomUUID(),
                                FIRM_ID,
                                Permission.VIEW_EXTERNAL_USER)));

        verifyNoInteractions(service);
    }

    @Test
    void externalActorWithoutPermissionCannotViewFirmUser() {

        stubTargetAccount();
        assertDeniedWith(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE, () ->
                handler.handle(new GetUserAccountQuery(TARGET_OID), external(UUID.randomUUID(), FIRM_ID)));
        verifyNoInteractions(service);
    }

    @Test
    void externalActorWithPermissionCanReachService() {

        stubTargetAccount();

        UserView expected = mock(UserView.class);

        when(service.getAccount(TARGET_OID, "actor-oid"))
                .thenReturn(expected);

        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null,
                null, "Role", true, null, Set.of(Permission.VIEW_EXTERNAL_USER));

        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firmId(FIRM_ID)
                .appRoles(Set.of(role))
                .build();

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actor-oid")
                .activeProfile(profile)
                .build();

        assertThat(
                handler.handle(
                        new GetUserAccountQuery(TARGET_OID),
                        actor))
                .isSameAs(expected);

        verify(service).getAccount(TARGET_OID, "actor-oid");
    }

    @Test
    void externalActorWithPermissionCannotViewUserInAnotherFirm() {

        stubTargetAccount();

        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false,
                null, null, "Role", true, null, Set.of(Permission.VIEW_EXTERNAL_USER));

        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firmId(UUID.randomUUID())  // different firm
                .appRoles(Set.of(role))
                .build();

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actor-oid")
                .activeProfile(profile)
                .build();

        assertDeniedWith(
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> handler.handle(
                        new GetUserAccountQuery(TARGET_OID),
                        actor));

        verifyNoInteractions(service);
    }

    @Test
    void internalActorCanViewInternalUser() {

        EntraUserDto user = mock(EntraUserDto.class);
        UserProfileDto profile = mock(UserProfileDto.class);

        when(profile.getUserType()).thenReturn(UserType.INTERNAL);
        when(user.getEntraOid()).thenReturn(TARGET_OID);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_OID))
                .thenReturn(user);

        UserView expected = mock(UserView.class);

        when(service.getAccount(TARGET_OID, "actor-oid"))
                .thenReturn(expected);

        AuthenticatedUserProfile actorProfile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .build();

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actor-oid")
                .activeProfile(actorProfile)
                .build();

        assertThat(handler.handle(
                new GetUserAccountQuery(TARGET_OID),
                actor))
                .isSameAs(expected);

        verify(service).getAccount(TARGET_OID, "actor-oid");
    }

    @Test
    void internalActorWithViewExternalPermissionCanViewExternalUser() {

        EntraUserDto user = mock(EntraUserDto.class);
        UserProfileDto profile = mock(UserProfileDto.class);
        FirmDto firm = mock(FirmDto.class);

        when(profile.getUserType()).thenReturn(UserType.EXTERNAL);
        when(profile.getFirm()).thenReturn(firm);
        when(firm.getId()).thenReturn(FIRM_ID);

        when(user.getEntraOid()).thenReturn(TARGET_OID);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_OID))
                .thenReturn(user);

        UserView expected = mock(UserView.class);

        when(service.getAccount(TARGET_OID, "actor-oid"))
                .thenReturn(expected);

        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false,
                null, null, "Role", true, null, Set.of(Permission.VIEW_EXTERNAL_USER));

        AuthenticatedUserProfile actorProfile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .appRoles(Set.of(role))
                .build();

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid("actor-oid")
                .activeProfile(actorProfile)
                .build();

        assertThat(handler.handle(
                new GetUserAccountQuery(TARGET_OID),
                actor))
                .isSameAs(expected);

        verify(service).getAccount(TARGET_OID, "actor-oid");
    }


    private void stubTargetAccount() {

        EntraUserDto user = mock(EntraUserDto.class);

        UserProfileDto profile = mock(UserProfileDto.class);
        FirmDto firm = mock(FirmDto.class);

        when(profile.getUserType()).thenReturn(UserType.EXTERNAL);
        when(profile.getFirm()).thenReturn(firm);
        when(firm.getId()).thenReturn(FIRM_ID);

        when(user.getEntraOid()).thenReturn(TARGET_OID);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));

        when(queryService.findUserAccountSummaryByUserEntraObjectId(TARGET_OID)).thenReturn(user);
    }

    private static void assertDeniedWith(PolicyDenialReason reason, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AuthorizationDeniedException.class, denied ->
                        Assertions.assertThat(denied.getAuthorizationResult()).isEqualTo(new PolicyAuthorizationDenial(reason)));
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