package uk.gov.justice.laa.datauserapi.application.query.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.datauserapi.application.query.handler.AuditUserDetailHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.DeletedUsersAuditHandler;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountVisibilityContextFactory;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAccountPolicy;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAuditUserPolicy;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewDeletedUsersAuditPolicy;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserDetailService;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserVisibilityPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.query.service.DeletedUsersAuditService;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.config.JacksonConfig;
import uk.gov.justice.laa.datauserapi.config.SecurityConfig;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.FirmDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditQueryController.class)
@Import({
    JacksonConfig.class,
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    AuditQueryController.class,
    AuditUserDetailHandler.class,
    DeletedUsersAuditHandler.class,
    RbacMethodSecurityConfig.class,
    CanViewAccountPolicy.class,
    CanViewAuditUserPolicy.class,
    CanViewDeletedUsersAuditPolicy.class,
    AuditUserVisibilityPolicyInputResolver.class,
    UserAccountVisibilityContextFactory.class,
    AuditQueryEndpointTest.ClassProxyConfig.class
})
@ActiveProfiles("test")
class AuditQueryEndpointTest {

    // Class proxies keep the controller's mappings visible behind the method-security proxy
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class ClassProxyConfig {
    }

    private static final String TARGET_OID = UUID.randomUUID().toString();
    private static final String DETAIL = "/api/v1/queries/audit/users/" + TARGET_OID;
    private static final String DELETED = "/api/v1/queries/audit/users/deleted";
    private static final String FORBIDDEN_DETAIL =
            "You do not possess the required scopes or permissions to perform this operation.";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AuditUserDetailService detailService;
    @MockitoBean
    private DeletedUsersAuditService deletedService;
    @MockitoBean
    private UserAccountQueryService queryService;
    @MockitoBean
    private JwtToAuthenticatedEntraUserConverter converter;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void resetMocks() {
        reset(detailService, deletedService, queryService);
    }

    @Test
    void unauthenticatedRequestsAreUnauthorisedWithProblemDetail() throws Exception {
        for (String path : List.of(DETAIL, DELETED)) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(detailService, deletedService, queryService);
    }

    @Test
    void readScopeIsNotEnoughForEitherEndpoint() throws Exception {
        for (String path : List.of(DETAIL, DELETED)) {
            mockMvc.perform(get(path).with(authentication(
                            actor("user_data.read", UserType.INTERNAL, Permission.VIEW_AUDIT_TABLE))))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(detailService, deletedService, queryService);
    }

    @Test
    void missingViewAuditTablePermissionIsForbiddenForEitherEndpoint() throws Exception {
        for (String path : List.of(DETAIL, DELETED)) {
            mockMvc.perform(get(path).with(authentication(
                            actor("user_data.admin", UserType.INTERNAL, Permission.VIEW_EXTERNAL_USER))))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(detailService, deletedService, queryService);
    }

    @Test
    void invalidIdentifierIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/queries/audit/users/not-a-uuid").with(authentication(auditActor())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(detailService, queryService);
    }

    @Test
    void unknownTargetIsTheSameGenericForbiddenAsAnInaccessibleOne() throws Exception {
        when(queryService.findUserAccountSummaryIfPresent(TARGET_OID)).thenReturn(Optional.empty());

        mockMvc.perform(get(DETAIL).with(authentication(auditActor())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(FORBIDDEN_DETAIL));

        verifyNoInteractions(detailService);
    }

    @Test
    void targetOutsideVisibilityIsForbidden() throws Exception {
        stubExternalTarget();

        mockMvc.perform(get(DETAIL).with(authentication(auditActor())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(FORBIDDEN_DETAIL));

        verifyNoInteractions(detailService);
    }

    @Test
    void visibleTargetReturnsOk() throws Exception {
        stubExternalTarget();
        when(detailService.getDetail(any(), any())).thenReturn(
                new uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView(null, List.of(), List.of()));

        mockMvc.perform(get(DETAIL).with(authentication(
                        actor("user_data.admin", UserType.INTERNAL, Permission.VIEW_AUDIT_TABLE,
                                Permission.VIEW_EXTERNAL_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles").isArray())
                .andExpect(jsonPath("$.accountStatusHistory").isArray());
    }

    @Test
    void externalActorIsForbiddenFromDeletedUsers() throws Exception {
        mockMvc.perform(get(DELETED).with(authentication(
                        actor("user_data.admin", UserType.EXTERNAL, Permission.VIEW_AUDIT_TABLE,
                                Permission.VIEW_EXTERNAL_USER))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(deletedService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page_number=-1", "page_size=0", "page_size=101", "page_number=abc", "page_size=abc"})
    void invalidPaginationIsBadRequest(String query) throws Exception {
        mockMvc.perform(get(DELETED + "?" + query).with(authentication(auditActor())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(deletedService);
    }

    @Test
    void noMatchesReturnsOkWithEmptyItemsAndPageMetadata() throws Exception {
        when(deletedService.getDeletedUsers(any(), any()))
                .thenReturn(new DeletedUserAuditPage(List.of(), new PageMetadata(0, 20, 0, 0)));

        mockMvc.perform(get(DELETED).param("email_query", "nobody").with(authentication(auditActor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.page_number").value(0))
                .andExpect(jsonPath("$.page.page_size").value(20))
                .andExpect(jsonPath("$.page.total_elements").value(0))
                .andExpect(jsonPath("$.page.total_pages").value(0));

        verify(deletedService).getDeletedUsers(
                org.mockito.ArgumentMatchers.argThat(query -> "nobody".equals(query.criteria().emailQuery())), any());
    }

    private void stubExternalTarget() {
        EntraUserDto user = EntraUserDto.builder()
                .entraOid(TARGET_OID)
                .userProfiles(Set.of(UserProfileDto.builder()
                        .userType(UserType.EXTERNAL)
                        .firm(FirmDto.builder().id(UUID.randomUUID()).build())
                        .build()))
                .build();
        when(queryService.findUserAccountSummaryIfPresent(TARGET_OID)).thenReturn(Optional.of(user));
    }

    private static UsernamePasswordAuthenticationToken auditActor() {
        return actor("user_data.admin", UserType.INTERNAL, Permission.VIEW_AUDIT_TABLE);
    }

    private static UsernamePasswordAuthenticationToken actor(String scope, UserType userType,
                                                              Permission... permissions) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "role", 0, "ROLE", false, null, null,
                null, false, null, Set.of(permissions));
        AuthenticatedEntraUser principal = AuthenticatedEntraUser.builder()
                .active(true)
                .id(UUID.randomUUID())
                .entraOid(UUID.randomUUID().toString())
                .activeProfile(AuthenticatedUserProfile.builder().activeProfile(true).userType(userType)
                        .firmId(userType == UserType.EXTERNAL ? UUID.randomUUID() : null)
                        .appRoles(Set.of(role)).build())
                .build();
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
