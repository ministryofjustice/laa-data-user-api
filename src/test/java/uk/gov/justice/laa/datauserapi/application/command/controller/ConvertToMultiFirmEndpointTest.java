package uk.gov.justice.laa.datauserapi.application.command.controller;

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
import uk.gov.justice.laa.datauserapi.application.command.controller.mapper.ConvertToMultiFirmCommandMapper;
import uk.gov.justice.laa.datauserapi.application.command.handler.DeactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.DeleteUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.ReactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserDetailsHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserDetailsMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmDomainMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmHandler;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.config.SecurityConfig;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAccountCommandController.class)
@Import({
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    UserAccountCommandController.class,
    ConvertToMultiFirmCommandMapper.class,
    ConvertToMultiFirmHandler.class,
    ConvertToMultiFirmDomainMapper.class,
    ConvertToMultiFirmEndpointTest.ClassProxyConfig.class
})
@ActiveProfiles("test")
class ConvertToMultiFirmEndpointTest {

    // The controller implements an interface; without class proxies the method-security proxy hides its mappings
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class ClassProxyConfig {
    }

    private static final String ENDPOINT = "/api/v1/commands/users/convert-to-multi-firm";
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final String VALID_BODY = "{\"userEntraObjectId\":\"" + TARGET_ID + "\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EntraUserCommandRepository repository;
    @MockitoBean
    private JwtToAuthenticatedEntraUserConverter converter;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private ReactivateUserHandler reactivateUserHandler;
    @MockitoBean
    private ReactivateUserMapper reactivateUserMapper;
    @MockitoBean
    private DeactivateUserHandler deactivateUserHandler;
    @MockitoBean
    private DeactivateUserMapper deactivateUserMapper;
    @MockitoBean
    private UpdateUserDetailsHandler updateUserDetailsHandler;
    @MockitoBean
    private UpdateUserDetailsMapper updateUserDetailsMapper;
    @MockitoBean
    private DeleteUserHandler deleteUserHandler;
    @MockitoBean
    private DeleteUserMapper deleteUserMapper;

    @Test
    void authorisedActorConvertsSingleFirmUser() throws Exception {
        EntraUser target = EntraUser.builder().entraOid(TARGET_ID.toString()).multiFirmUser(false).build();
        when(repository.findByEntraOidForUpdate(TARGET_ID.toString())).thenReturn(Optional.of(target));

        mockMvc.perform(post(ENDPOINT).with(authentication(actor("user_data.admin", true, true)))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(repository).save(target);
    }

    @Test
    void unauthenticatedRequestIsUnauthorised() throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user_data.read", "other.scope"})
    void missingAdminScopeIsForbidden(String scope) throws Exception {
        mockMvc.perform(post(ENDPOINT).with(authentication(actor(scope, true, true)))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(repository);
    }

    @Test
    void missingEditExternalUserPermissionIsForbidden() throws Exception {
        assertForbiddenAndNoRepositoryAccess(actor("user_data.admin", false, true));
    }

    @Test
    void missingConvertPermissionIsForbidden() throws Exception {
        assertForbiddenAndNoRepositoryAccess(actor("user_data.admin", true, false));
    }

    @Test
    void unknownTargetIsNotFound() throws Exception {
        when(repository.findByEntraOidForUpdate(TARGET_ID.toString())).thenReturn(Optional.empty());

        mockMvc.perform(post(ENDPOINT).with(authentication(actor("user_data.admin", true, true)))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));

        verify(repository, never()).save(any());
    }

    @Test
    void alreadyMultiFirmTargetIsConflict() throws Exception {
        EntraUser target = EntraUser.builder().entraOid(TARGET_ID.toString()).multiFirmUser(true).build();
        when(repository.findByEntraOidForUpdate(TARGET_ID.toString())).thenReturn(Optional.of(target));

        mockMvc.perform(post(ENDPOINT).with(authentication(actor("user_data.admin", true, true)))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"userEntraObjectId\":null}",
        "{\"userEntraObjectId\":\"not-a-uuid\"}",
        "{\"userEntraObjectId\":",
        "[]"
    })
    void invalidBodyIsBadRequest(String body) throws Exception {
        mockMvc.perform(post(ENDPOINT).with(authentication(actor("user_data.admin", true, true)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(repository);
    }

    @Test
    void absentBodyIsBadRequest() throws Exception {
        mockMvc.perform(post(ENDPOINT).with(authentication(actor("user_data.admin", true, true)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(repository);
    }

    private void assertForbiddenAndNoRepositoryAccess(UsernamePasswordAuthenticationToken actor) throws Exception {
        mockMvc.perform(post(ENDPOINT).with(authentication(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(repository);
    }

    private static UsernamePasswordAuthenticationToken actor(String scope, boolean editExternal, boolean convert) {
        Set<Permission> permissions = new java.util.HashSet<>();
        if (editExternal) {
            permissions.add(Permission.EDIT_EXTERNAL_USER);
        }
        if (convert) {
            permissions.add(Permission.CONVERT_USER_TO_MULTI_FIRM);
        }
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "role", 0, "ROLE", false, null, null,
                null, false, null, permissions);
        AuthenticatedEntraUser principal = AuthenticatedEntraUser.builder()
                .id(UUID.randomUUID()).entraOid(UUID.randomUUID().toString())
                .activeProfile(AuthenticatedUserProfile.builder().activeProfile(true).userType(UserType.INTERNAL)
                        .appRoles(Set.of(role)).build())
                .build();
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
