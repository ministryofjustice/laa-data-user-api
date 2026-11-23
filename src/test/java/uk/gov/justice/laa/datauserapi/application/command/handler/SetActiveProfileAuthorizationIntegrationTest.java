package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import uk.gov.justice.laa.datauserapi.application.command.policy.SetActiveProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.command.service.SetActiveProfilePolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.command.service.SetActiveProfileService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyAuthorizationDenial;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;
import uk.gov.justice.laa.datauserapi.security.rbac.RbacMethodSecurityConfig;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(SetActiveProfileAuthorizationIntegrationTest.TestConfig.class)
class SetActiveProfileAuthorizationIntegrationTest {

    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({RbacMethodSecurityConfig.class, SetActiveProfileHandler.class, SetActiveProfilePolicy.class,
        SetActiveProfilePolicyInputResolver.class})
    static class TestConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        UserProfileCommandRepository userProfileCommandRepository() {
            return mock(UserProfileCommandRepository.class);
        }

        @Bean
        SetActiveProfileService setActiveProfileService() {
            return mock(SetActiveProfileService.class);
        }
    }

    @Autowired
    private SetActiveProfileHandler handler;
    @Autowired
    private UserProfileCommandRepository repository;
    @Autowired
    private SetActiveProfileService service;

    @BeforeEach
    void setUp() {
        reset(repository, service);
        when(repository.findForSetActiveProfileAuthorization(PROFILE_ID))
                .thenReturn(Optional.of(UserProfile.builder()
                        .id(PROFILE_ID)
                        .userType(UserType.EXTERNAL)
                        .entraUser(EntraUser.builder().entraOid(USER_ID.toString()).build())
                        .build()));
    }

    @Test
    void externalActorLinkedToRequestedProfileIsAuthorisedByTheSpringProxy() {
        AuthenticatedEntraUser actor = externalActor(PROFILE_ID);
        SetActiveProfileCommand command = command();
        CommandResult expected = CommandResult.success("updated");
        when(service.setActiveProfile(command, actor)).thenReturn(expected);

        assertThat(handler.handle(command, actor)).isSameAs(expected);
        verify(service).setActiveProfile(command, actor);
    }

    @Test
    void externalActorNotLinkedToRequestedProfileIsDeniedBeforeServiceRuns() {
        AuthenticatedEntraUser actor = externalActor(UUID.randomUUID());

        assertDenied(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE,
                () -> handler.handle(command(), actor));

        verifyNoInteractions(service);
    }

    @Test
    void internalActorIsDeniedBeforeServiceRuns() {
        AuthenticatedEntraUser actor = actor(UserType.INTERNAL, PROFILE_ID);

        assertDenied(PolicyDenialReason.ACTOR_NOT_EXTERNAL_USER,
                () -> handler.handle(command(), actor));

        verifyNoInteractions(service);
    }

    @Test
    void internalTargetProfileIsDeniedToAnExternalActor() {
        when(repository.findForSetActiveProfileAuthorization(PROFILE_ID))
                .thenReturn(Optional.of(UserProfile.builder()
                        .id(PROFILE_ID)
                        .userType(UserType.INTERNAL)
                        .entraUser(EntraUser.builder().entraOid(USER_ID.toString()).build())
                        .build()));

        assertDenied(PolicyDenialReason.TARGET_PROFILE_INTERNAL,
                () -> handler.handle(command(), externalActor(PROFILE_ID)));

        verifyNoInteractions(service);
    }

    @Test
    void actorWithoutAnActiveProfileIsDeniedBeforeServiceRuns() {
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().userProfiles(Set.of()).build();

        assertDenied(PolicyDenialReason.ACTOR_NOT_EXTERNAL_USER,
                () -> handler.handle(command(), actor));

        verifyNoInteractions(service);
    }

    @Test
    void nonexistentProfileIsAllowedPastAuthorisationSoTheCommandCanReturnNotFound() {
        when(repository.findForSetActiveProfileAuthorization(PROFILE_ID)).thenReturn(Optional.empty());
        AuthenticatedEntraUser actor = externalActor(PROFILE_ID);
        SetActiveProfileCommand command = command();
        when(service.setActiveProfile(command, actor)).thenReturn(CommandResult.success("updated"));

        assertThat(handler.handle(command, actor).success()).isTrue();

        verify(service).setActiveProfile(command, actor);
    }

    @Test
    void mismatchedUserIsNotRevealedByAuthorisationAndContinuesToCommandNotFoundHandling() {
        when(repository.findForSetActiveProfileAuthorization(PROFILE_ID))
                .thenReturn(Optional.of(UserProfile.builder()
                        .id(PROFILE_ID)
                        .userType(UserType.EXTERNAL)
                        .entraUser(EntraUser.builder().entraOid(UUID.randomUUID().toString()).build())
                        .build()));
        AuthenticatedEntraUser actor = externalActor(PROFILE_ID);
        SetActiveProfileCommand command = command();
        when(service.setActiveProfile(command, actor)).thenReturn(CommandResult.success("updated"));

        assertThat(handler.handle(command, actor).success()).isTrue();

        verify(service).setActiveProfile(command, actor);
    }

    private static SetActiveProfileCommand command() {
        return new SetActiveProfileCommand(USER_ID, PROFILE_ID);
    }

    private static AuthenticatedEntraUser externalActor(UUID linkedProfileId) {
        return actor(UserType.EXTERNAL, linkedProfileId);
    }

    private static AuthenticatedEntraUser actor(UserType type, UUID linkedProfileId) {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(linkedProfileId)
                .activeProfile(true)
                .userType(type)
                .build();
        return AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .activeProfile(profile)
                .userProfiles(Set.of(profile))
                .build();
    }

    private static void assertDenied(PolicyDenialReason reason, ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AuthorizationDeniedException.class, denied ->
                        assertThat(denied.getAuthorizationResult())
                                .isEqualTo(new PolicyAuthorizationDenial(reason)));
    }
}
