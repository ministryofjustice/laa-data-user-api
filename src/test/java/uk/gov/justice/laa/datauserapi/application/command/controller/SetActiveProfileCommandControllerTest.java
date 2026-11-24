package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.datauserapi.application.command.handler.SetActiveProfileHandler;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.config.JacksonConfig;
import uk.gov.justice.laa.datauserapi.config.SecurityConfig;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.ForbiddenResponseConstants;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SetActiveProfileCommandController.class)
@Import({JacksonConfig.class, SecurityConfig.class, GlobalExceptionHandler.class, SetActiveProfileCommandController.class})
@ActiveProfiles("test")
class SetActiveProfileCommandControllerTest {

    private static final String ENDPOINT = "/api/v1/commands/users/set-active-profile";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final String VALID_BODY = """
            {"userEntraObjectId":"%s","userProfileId":"%s"}
            """.formatted(USER_ID, PROFILE_ID);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SetActiveProfileHandler handler;
    @MockitoBean
    private JwtToAuthenticatedEntraUserConverter converter;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authorisedRequestReturnsCommandResult() throws Exception {
        AuthenticatedEntraUser actor = externalActor();
        CommandResult result = CommandResult.success("Active profile set successfully");
        when(handler.handle(new SetActiveProfileCommand(USER_ID, PROFILE_ID), actor)).thenReturn(result);

        mockMvc.perform(post(ENDPOINT).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(adminAuthentication(actor)))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Active profile set successfully"));

        verify(handler).handle(new SetActiveProfileCommand(USER_ID, PROFILE_ID), actor);
    }

    @Test
    void requestWithoutTokenReturnsUnauthorizedProblemDetail() throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(handler);
    }

    @Test
    void requestWithInvalidTokenReturnsUnauthorizedProblemDetail() throws Exception {
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(post(ENDPOINT).header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(handler);
    }

    @Test
    void requestWithoutAdminScopeReturnsForbiddenProblemDetail() throws Exception {
        mockMvc.perform(post(ENDPOINT).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(tokenWithScope(externalActor(), "user_data.read")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(handler);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidRequestBodyReturnsBadRequestProblemDetail(String body) throws Exception {
        mockMvc.perform(post(ENDPOINT).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(adminAuthentication(externalActor())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(handler);
    }

    @Test
    void missingRequestBodyReturnsBadRequestProblemDetail() throws Exception {
        mockMvc.perform(post(ENDPOINT).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(adminAuthentication(externalActor())))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(handler);
    }

    @Test
    void unknownUserOrProfileIsReturnedAsForbidden() throws Exception {
        doThrow(new UserProfileNotFoundException(PROFILE_ID))
                .when(handler).handle(any(SetActiveProfileCommand.class), any(AuthenticatedEntraUser.class));

        mockMvc.perform(post(ENDPOINT).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(adminAuthentication(externalActor())))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(ForbiddenResponseConstants.DETAIL));
    }

    private static UsernamePasswordAuthenticationToken adminAuthentication(AuthenticatedEntraUser actor) {
        return tokenWithScope(actor, "user_data.admin");
    }

    private static UsernamePasswordAuthenticationToken tokenWithScope(AuthenticatedEntraUser actor, String scope) {
        return new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }

    private static Stream<String> invalidBodies() {
        return Stream.of(
                "{}",
                "{\"userEntraObjectId\":null,\"userProfileId\":\"%s\"}".formatted(PROFILE_ID),
                "{\"userEntraObjectId\":\"not-a-uuid\",\"userProfileId\":\"%s\"}".formatted(PROFILE_ID),
                "{\"userEntraObjectId\":\"%s\"}".formatted(USER_ID),
                "{\"userEntraObjectId\":\"%s\",\"userProfileId\":\"not-a-uuid\"}".formatted(USER_ID),
                "{\"userEntraObjectId\":\"%s\",\"userProfileId\":null}".formatted(USER_ID),
                "{\"userEntraObjectId\":");
    }

    private static AuthenticatedEntraUser externalActor() {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(PROFILE_ID)
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .build();
        return AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .activeProfile(profile)
                .userProfiles(java.util.Set.of(profile))
                .build();
    }
}
