package uk.gov.justice.laa.datauserapi.application.query.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfileAppRolesHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfileAppsHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfileOfficesHandler;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;

class UserProfileAccessQueryControllerTest {

    private static final String BASE = "/api/v1/queries/user-profiles/";

    private final GetUserProfileAppRolesHandler appRolesHandler = mock(GetUserProfileAppRolesHandler.class);
    private final GetUserProfileAppsHandler appsHandler = mock(GetUserProfileAppsHandler.class);
    private final GetUserProfileOfficesHandler officesHandler = mock(GetUserProfileOfficesHandler.class);
    private final UUID profileId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new UserProfileAccessQueryController(appRolesHandler, appsHandler, officesHandler))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void appRolesPassesBothFilters() throws Exception {
        UUID appId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        when(appRolesHandler.handle(eq(profileId), eq(appId), eq(clientId), any()))
                .thenReturn(new AppRoleViewList(List.of()));

        mockMvc.perform(get(BASE + profileId + "/app-roles")
                        .param("app_id", appId.toString())
                        .param("appEntraClientId", clientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(appRolesHandler).handle(eq(profileId), eq(appId), eq(clientId), any());
    }

    @Test
    void appRolesWithoutFiltersPassesNulls() throws Exception {
        when(appRolesHandler.handle(eq(profileId), isNull(), isNull(), any()))
                .thenReturn(new AppRoleViewList(List.of()));

        mockMvc.perform(get(BASE + profileId + "/app-roles")).andExpect(status().isOk());
    }

    @Test
    void appsReturnsApplicationsWithBothIdentifiers() throws Exception {
        UUID appId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        when(appsHandler.handle(eq(profileId), any())).thenReturn(new AppViewList(List.of(
                new AppView(appId.toString(), "App", "Desc", "https://app", true, 1,
                        UUID.randomUUID().toString(), clientId.toString()))));

        mockMvc.perform(get(BASE + profileId + "/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].appId").value(appId.toString()))
                .andExpect(jsonPath("$.items[0].appEntraClientId").value(clientId.toString()))
                .andExpect(jsonPath("$.items[0].appEntraObjectId").exists());
    }

    @Test
    void officesReturnsItems() throws Exception {
        when(officesHandler.handle(eq(profileId), any())).thenReturn(new OfficeViewList(List.of(
                new OfficeView("o1", "f1", "AB1 2CD", "1 Street", null, null, "Town"))));

        mockMvc.perform(get(BASE + profileId + "/offices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].officeId").value("o1"));
    }

    @Test
    void malformedIdentifiersReturnBadRequest() throws Exception {
        mockMvc.perform(get(BASE + "not-a-uuid/app-roles")).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "not-a-uuid/apps")).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "not-a-uuid/offices")).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + profileId + "/app-roles").param("app_id", "bad"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + profileId + "/app-roles").param("appEntraClientId", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(appRolesHandler, appsHandler, officesHandler);
    }

    @Test
    void inaccessibleProfileReturnsGenericProblemJsonForbiddenWithoutLeakingDiagnostics() throws Exception {
        when(appsHandler.handle(eq(profileId), any())).thenThrow(new AccessDeniedException("User profile access denied: PROFILE_NOT_VISIBLE"));

        mockMvc.perform(get(BASE + profileId + "/apps"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(
                        "You do not possess the required scopes or permissions to perform this operation."));
    }
}
