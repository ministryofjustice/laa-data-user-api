package uk.gov.justice.laa.datauserapi.pact;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.datauserapi.application.query.controller.UserAccountQueryController;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetActiveProfileHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserAccountHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfilesHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.SearchUsersHandler;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuthenticatedEntraUserQueryRepository;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.config.SecurityConfig;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryView;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * HTTP contract verification with real controllers, JSON binding and authentication.
 * Business handlers are stubbed by provider states; database behaviour is covered separately.
 */
@SpringBootTest(classes = AbstractProviderPactTests.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.name=pact", "sentry.enabled=false"})
public abstract class AbstractProviderPactTests {

    protected static final String ACTOR_OID = "00000000-0000-0000-0000-000000000042";

    @LocalServerPort
    private int port;

    @MockitoBean
    protected AuthenticatedEntraUserQueryRepository authenticatedUsers;
    @MockitoBean
    protected SearchUsersHandler searchUsersHandler;
    @MockitoBean
    private UserAccountQueryService queryService;
    @MockitoBean
    private GetUserAccountHandler getUserAccountHandler;
    @MockitoBean
    private GetUserProfilesHandler getUserProfilesHandler;
    @MockitoBean
    private GetActiveProfileHandler getActiveProfileHandler;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
        when(authenticatedUsers.findByEntraOid(ACTOR_OID)).thenReturn(Optional.of(EntraUser.builder()
                .id(UUID.fromString(ACTOR_OID)).entraOid(ACTOR_OID)
                .firstName("Pact Verification User").lastName("User")
                .email("pact.user@example.com").userProfiles(Set.of()).build()));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("users exist for the search criteria")
    public void usersExist() {
        when(searchUsersHandler.handle(argThat(query -> matchesSearch(query, "jane"))))
                .thenReturn(new UserAccountSummaryPage(List.of(new UserAccountSummaryView(
                        "00000000-0000-0000-0000-0000000000aa", "jane.doe@example.com",
                        "Jane", "Doe", UserAccountStatus.ACTIVE, false, false,
                        null, null, null, 1)), new PageMetadata(0, 20, 1, 1)));
    }

    @State("no users match the search criteria")
    public void noMatchingUsers() {
        when(searchUsersHandler.handle(argThat(query -> matchesSearch(query, "nobody"))))
                .thenReturn(new UserAccountSummaryPage(List.of(), new PageMetadata(0, 20, 0, 0)));
    }

    private boolean matchesSearch(SearchUsersQuery query, String user) {
        return query != null && ACTOR_OID.equals(query.actorOid())
                && user.equals(query.criteria().user())
                && Integer.valueOf(0).equals(query.criteria().pageNumber())
                && Integer.valueOf(20).equals(query.criteria().pageSize())
                && List.of("email").equals(query.criteria().sortBy());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class})
    @Import({SecurityConfig.class, JwtToAuthenticatedEntraUserConverter.class,
            UserAccountQueryController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("pact-verification-user")
                    .claim("oid", ACTOR_OID)
                    .claim("scp", "user_data.read")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }
}
