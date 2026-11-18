package uk.gov.justice.laa.datauserapi.security.opa.trial;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import uk.gov.justice.laa.datauserapi.application.command.controller.ProfileRoleChangeController;
import uk.gov.justice.laa.datauserapi.application.command.handler.ProfileRoleChangeHandler;
import uk.gov.justice.laa.datauserapi.application.command.service.ProfileRoleChangeService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.ProfileRoleChangeCommand;
import uk.gov.justice.laa.datauserapi.application.query.controller.ProfileTrialQueryController;
import uk.gov.justice.laa.datauserapi.application.query.handler.ProfileTrialQueryHandler;
import uk.gov.justice.laa.datauserapi.application.query.profiletrial.ProfileTrialQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.ProfileTrialQueryService;
import uk.gov.justice.laa.datauserapi.application.shared.authorisation.ProfileTrialInputResolver;
import uk.gov.justice.laa.datauserapi.application.shared.mapper.ProfileTrialFactsLoader;
import uk.gov.justice.laa.datauserapi.application.shared.policy.ProfileTrialDecisionPolicy;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.request.AssignAppRolesRequest;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(ProfileTrialHandlerIntegrationTest.Config.class)
@TestPropertySource(properties = {"app.authorization.legacy-policies-enabled=false", "app.authorization.opa-enabled=false"})
class ProfileTrialHandlerIntegrationTest {
    @Autowired private ProfileTrialQueryHandler queries;
    @Autowired private ProfileRoleChangeHandler commands;
    @Autowired private ProfileTrialQueryController queryController;
    @Autowired private ProfileRoleChangeController commandController;
    @Autowired private ProfileTrialFactsLoader loader;
    @Autowired private ProfileTrialAuthorizer authorizer;
    @Autowired private ProfileTrialQueryService reads;
    @Autowired private ProfileRoleChangeService writes;
    @Autowired private ProfileTrialDecisionPolicy policy;
    private AuthenticatedEntraUser actor;
    private ProfileTrialInput input;
    private ProfileTrialDecision allowed;

    @BeforeEach
    void setUp() {
        reset(loader, authorizer, reads, writes);
        actor = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).build();
        input = new ProfileTrialInput(ProfileTrialOperation.SEARCH,
                new ProfileTrialInput.Actor(actor.id(), UUID.randomUUID(), "actor", UserType.INTERNAL,
                        null, Set.of(), Set.of(), Set.of(), true), null, Map.of(), Set.of(), Set.of(), true);
        allowed = new ProfileTrialDecision(true, ProfileTrialDecision.Reason.ALLOWED,
                new ProfileTrialDecision.Filters(null, Set.of(UserType.EXTERNAL), Set.of()));
        when(loader.load(any())).thenReturn(input);
        when(authorizer.decide(input)).thenReturn(allowed);
        authenticate("SCOPE_user_data.admin");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void realQueryProxyBindsFreshContextAndOverwritesForgedPlans() {
        var forged = new ProfileTrialPlan(input, ProfileTrialDecision.deny(ProfileTrialDecision.Reason.FIRM_SCOPE));
        var request = new ProfileTrialRequest(ProfileTrialOperation.SEARCH, null, Set.of(), Map.of(), actor);
        queries.handle(new ProfileTrialQuery(request, null, forged), actor);
        var capture = ArgumentCaptor.forClass(ProfileTrialQuery.class);
        verify(reads).search(capture.capture());
        assertThat(capture.getValue().plan()).isEqualTo(new ProfileTrialPlan(input, allowed));
        verify(loader, times(1)).load(request);
        verify(authorizer, times(1)).decide(input);
    }

    @Test
    void realCommandProxyBindsTheTrustedPlan() {
        var request = new ProfileTrialRequest(ProfileTrialOperation.ASSIGN, UUID.randomUUID(), Set.of(), Map.of(), actor);
        commands.handle(new ProfileRoleChangeCommand(request, null), actor);
        var capture = ArgumentCaptor.forClass(ProfileRoleChangeCommand.class);
        verify(writes).change(capture.capture());
        assertThat(capture.getValue().plan()).isEqualTo(new ProfileTrialPlan(input, allowed));
    }

    @Test
    void policyDenialBlocksReadsAndWritesEvenWhenBothEngineFlagsAreOff() {
        when(authorizer.decide(input)).thenReturn(ProfileTrialDecision.deny(ProfileTrialDecision.Reason.MISSING_PERMISSION));
        var request = new ProfileTrialRequest(ProfileTrialOperation.SEARCH, null, Set.of(), Map.of(), actor);
        assertThatThrownBy(() -> queries.handle(new ProfileTrialQuery(request, null, null), actor))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> commands.handle(new ProfileRoleChangeCommand(request, null), actor))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reads, writes);
    }

    @Test
    void resolvedFactsCannotBeUsedForAnotherActor() {
        var other = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).build();
        var request = new ProfileTrialRequest(ProfileTrialOperation.SEARCH, null, Set.of(), Map.of(), actor);
        assertThatThrownBy(() -> queries.handle(new ProfileTrialQuery(request, null, null), other))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reads);
    }

    @Test
    void nullPolicyInputsDenyBeforeBusinessExecution() {
        assertThat(policy.evaluate(null, actor).granted()).isFalse();
        assertThat(policy.evaluate(new ProfileTrialPlan(null, allowed), actor).granted()).isFalse();
        assertThat(policy.evaluate(new ProfileTrialPlan(input, null), actor).granted()).isFalse();
        assertThat(policy.evaluate(new ProfileTrialPlan(input, allowed), null).granted()).isFalse();
        verifyNoInteractions(reads, writes);
    }

    @Test
    void invalidContextBindingRaisesConfigurationErrorBeforeInvocation() {
        ProfileRoleChangeCommand invalid = mock(ProfileRoleChangeCommand.class);
        org.mockito.Mockito.<Class<? extends uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy<ProfileTrialPlan>>>when(
                invalid.contextPolicyType()).thenReturn(ProfileTrialDecisionPolicy.class);
        when(invalid.authorizationContextType()).thenReturn(ProfileTrialPlan.class);
        when(invalid.request()).thenReturn(new ProfileTrialRequest(ProfileTrialOperation.ASSIGN, UUID.randomUUID(), Set.of(), Map.of(), actor));
        assertThatThrownBy(() -> commands.handle(invalid, actor))
                .isInstanceOf(uk.gov.justice.laa.datauserapi.security.rbac.InvalidAuthorizationConfigurationException.class);
        verifyNoInteractions(writes);
    }

    @Test
    void opaOutageDoesNotExecuteTheHandler() {
        when(authorizer.decide(input)).thenThrow(new uk.gov.justice.laa.datauserapi.exception.OpaUnavailableException("Unavailable"));
        var request = new ProfileTrialRequest(ProfileTrialOperation.SEARCH, null, Set.of(), Map.of(), actor);
        assertThatThrownBy(() -> queries.handle(new ProfileTrialQuery(request, null, null), actor))
                .isInstanceOf(uk.gov.justice.laa.datauserapi.exception.OpaUnavailableException.class);
        verifyNoInteractions(reads);
    }

    @Test
    void controllersEnforceReadAndAdminScopesBeforeResolvingFacts() {
        authenticate("SCOPE_unrelated");
        assertThatThrownBy(() -> queryController.detail(UUID.randomUUID(), actor)).isInstanceOf(AccessDeniedException.class);
        authenticate("SCOPE_user_data.read");
        assertThatThrownBy(() -> commandController.assign(new AssignAppRolesRequest(UUID.randomUUID(), Set.of()), actor))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(loader, reads, writes);
    }

    @Test
    void httpBindingPreservesSortDirectionAndValidation() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(queryController, commandController)
                .setControllerAdvice(new uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/queries/user-profiles")
                .param("page_number", "2").param("page_size", "10").param("sort_by", "id,desc")
                .param("office_id", "123456"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var capture = ArgumentCaptor.forClass(ProfileTrialQuery.class);
        verify(reads).search(capture.capture());
        assertThat(capture.getValue().criteria().pageNumber()).isEqualTo(2);
        assertThat(capture.getValue().criteria().sortBy()).containsExactly("id,desc");
        assertThat(capture.getValue().criteria().officeId()).isEqualTo("123456");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/queries/user-profiles")
                .param("page_size", "101"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void missingAndInaccessibleProfilesReturnTheSameGenericHttp403() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(queryController)
                .setControllerAdvice(new uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        when(authorizer.decide(input))
                .thenReturn(ProfileTrialDecision.deny(ProfileTrialDecision.Reason.TARGET_NOT_FOUND),
                        ProfileTrialDecision.deny(ProfileTrialDecision.Reason.FIRM_SCOPE));
        String path = "/api/v1/queries/user-profiles/" + UUID.randomUUID();
        String missing = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        String inaccessible = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertThat(missing).isEqualTo(inaccessible).doesNotContain("TARGET_NOT_FOUND", "FIRM_SCOPE", "actorId", "firmId");
        verifyNoInteractions(reads);
    }

    private void authenticate(String scope) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor, null, scope));
    }

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @Import({RbacMethodSecurityConfig.class, ProfileTrialDecisionPolicy.class, ProfileTrialInputResolver.class,
        ProfileTrialQueryHandler.class, ProfileRoleChangeHandler.class,
        ProfileTrialQueryController.class, ProfileRoleChangeController.class})
    static class Config {
        @Bean
        ProfileTrialFactsLoader loader() {
            return mock(ProfileTrialFactsLoader.class);
        }

        @Bean
        ProfileTrialAuthorizer authorizer() {
            return mock(ProfileTrialAuthorizer.class);
        }

        @Bean
        ProfileTrialQueryService reads() {
            return mock(ProfileTrialQueryService.class);
        }

        @Bean
        ProfileRoleChangeService writes() {
            return mock(ProfileRoleChangeService.class);
        }
    }
}
