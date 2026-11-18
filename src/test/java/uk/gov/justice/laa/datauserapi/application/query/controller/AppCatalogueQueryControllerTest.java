package uk.gov.justice.laa.datauserapi.application.query.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetAppByIdHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetAppRoleByIdHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetAppRolesByAppHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetAppsHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetRoleAssignmentRestrictionsHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppRoleByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppRolesByAppQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppsQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetRoleAssignmentRestrictionsQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.dto.RoleAssignmentRestrictionView;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.RoleAssignmentRestrictionViewPage;
import uk.gov.justice.laa.datauserapi.exception.AppNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.AppRoleNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;

class AppCatalogueQueryControllerTest {

    private static final String BASE = "/api/v1/queries";

    private final GetAppsHandler appsHandler = mock(GetAppsHandler.class);
    private final GetAppByIdHandler appByIdHandler = mock(GetAppByIdHandler.class);
    private final GetAppRolesByAppHandler appRolesHandler = mock(GetAppRolesByAppHandler.class);
    private final GetAppRoleByIdHandler appRoleByIdHandler = mock(GetAppRoleByIdHandler.class);
    private final GetRoleAssignmentRestrictionsHandler restrictionsHandler =
            mock(GetRoleAssignmentRestrictionsHandler.class);
    private final UUID appId = UUID.randomUUID();
    private ValidatorFactory validatorFactory;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        ProxyFactory validated = new ProxyFactory(new AppRoleQueryController(appRoleByIdHandler, restrictionsHandler));
        validated.addAdvice(new MethodValidationInterceptor(validatorFactory.getValidator()));
        mockMvc = standaloneSetup(
                new AppQueryController(appsHandler, appByIdHandler, appRolesHandler), validated.getProxy())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void appsReturnsApplicationsWithBothIdentifiers() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(appsHandler.handle(eq(new GetAppsQuery()), any())).thenReturn(new AppViewList(List.of(
                new AppView(appId.toString(), "App", "Desc", "https://app", true, 1,
                        UUID.randomUUID().toString(), clientId.toString()))));

        mockMvc.perform(get(BASE + "/apps"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items[0].appId").value(appId.toString()))
                .andExpect(jsonPath("$.items[0].appEntraClientId").value(clientId.toString()))
                .andExpect(jsonPath("$.items[0].appEntraObjectId").exists())
                .andExpect(jsonPath("$.items[0].active").value(true));
    }

    @Test
    void appByIdUsesTheInternalAppId() throws Exception {
        when(appByIdHandler.handle(eq(new GetAppByIdQuery(appId)), any())).thenReturn(
                new AppView(appId.toString(), "App", "Desc", "https://app", true, 1, null, null));

        mockMvc.perform(get(BASE + "/apps/" + appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(appId.toString()));
    }

    @Test
    void unknownOrInactiveAppReturnsProblemJsonNotFound() throws Exception {
        when(appByIdHandler.handle(eq(new GetAppByIdQuery(appId)), any())).thenThrow(new AppNotFoundException(appId));
        when(appRolesHandler.handle(eq(new GetAppRolesByAppQuery(appId, null)), any()))
                .thenThrow(new AppNotFoundException(appId));

        for (String path : List.of("/apps/" + appId, "/apps/" + appId + "/roles")) {
            mockMvc.perform(get(BASE + path))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value(not(containsString(appId.toString()))));
        }
    }

    @Test
    void appRolesPassesTheUserTypeFilterAndReturnsAnEmptyList() throws Exception {
        when(appRolesHandler.handle(eq(new GetAppRolesByAppQuery(appId, UserType.EXTERNAL)), any()))
                .thenReturn(new AppRoleViewList(List.of()));

        mockMvc.perform(get(BASE + "/apps/" + appId + "/roles").param("user_type", "EXTERNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void appRolesWithoutFilterPassesNull() throws Exception {
        when(appRolesHandler.handle(eq(new GetAppRolesByAppQuery(appId, null)), any()))
                .thenReturn(new AppRoleViewList(List.of()));

        mockMvc.perform(get(BASE + "/apps/" + appId + "/roles")).andExpect(status().isOk());
    }

    @Test
    void appRoleByIdReturnsTheRoleWithItsClientId() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        when(appRoleByIdHandler.handle(eq(new GetAppRoleByIdQuery(roleId)), any())).thenReturn(new AppRoleView(
                roleId.toString(), appId.toString(), "App", clientId.toString(), "Role", "Desc", false, "ROLE",
                false, 1, "BOTH", List.of()));

        mockMvc.perform(get(BASE + "/app-roles/" + roleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appRoleId").value(roleId.toString()))
                .andExpect(jsonPath("$.appId").value(appId.toString()))
                .andExpect(jsonPath("$.appEntraClientId").value(clientId.toString()));
    }

    @Test
    void unknownOrIneligibleRoleReturnsNotFound() throws Exception {
        UUID roleId = UUID.randomUUID();
        when(appRoleByIdHandler.handle(eq(new GetAppRoleByIdQuery(roleId)), any()))
                .thenThrow(new AppRoleNotFoundException(roleId));

        mockMvc.perform(get(BASE + "/app-roles/" + roleId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void restrictionsDefaultToTheFirstPageOfTwenty() throws Exception {
        when(restrictionsHandler.handle(eq(new GetRoleAssignmentRestrictionsQuery(null, 0, 20)), any()))
                .thenReturn(new RoleAssignmentRestrictionViewPage(List.of(), new PageMetadata(0, 20, 0, 0)));

        mockMvc.perform(get(BASE + "/role-restrictions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.page_number").value(0))
                .andExpect(jsonPath("$.page.page_size").value(20))
                .andExpect(jsonPath("$.page.total_elements").value(0));
    }

    @Test
    void restrictionsPassTheAppFilterAndPaging() throws Exception {
        when(restrictionsHandler.handle(eq(new GetRoleAssignmentRestrictionsQuery(appId, 2, 100)), any()))
                .thenReturn(new RoleAssignmentRestrictionViewPage(List.of(new RoleAssignmentRestrictionView(
                        "r", "Role", appId.toString(), "client", "App", "a", "Assigning")),
                        new PageMetadata(2, 100, 201, 3)));

        mockMvc.perform(get(BASE + "/role-restrictions")
                        .param("app_id", appId.toString()).param("page_number", "2").param("page_size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].appId").value(appId.toString()))
                .andExpect(jsonPath("$.items[0].appEntraClientId").value("client"))
                .andExpect(jsonPath("$.page.total_pages").value(3));
    }

    @Test
    void malformedIdentifiersUserTypeAndPaginationReturnBadRequest() throws Exception {
        List<String> paths = List.of("/apps/not-a-uuid", "/apps/not-a-uuid/roles", "/app-roles/not-a-uuid");
        for (String path : paths) {
            mockMvc.perform(get(BASE + path)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(BASE + "/apps/" + appId + "/roles").param("user_type", "BOTH"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/role-restrictions").param("app_id", "bad")).andExpect(status().isBadRequest());
        for (String[] invalid : new String[][] {
                {"page_number", "-1"}, {"page_number", "x"}, {"page_size", "0"}, {"page_size", "101"},
                {"page_size", "x"}}) {
            mockMvc.perform(get(BASE + "/role-restrictions").param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        verifyNoInteractions(appByIdHandler, appRolesHandler, appRoleByIdHandler, restrictionsHandler);
    }

    @Test
    void deniedActorGetsForbidden() throws Exception {
        when(appsHandler.handle(any(), any()))
                .thenThrow(new AuthorizationDeniedException("denied", new AuthorizationDecision(false)));

        mockMvc.perform(get(BASE + "/apps"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verify(appsHandler).handle(any(), any());
    }
}
