package uk.gov.justice.laa.datauserapi.pact;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.TargetRequestFilter;
import java.util.List;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.integration.BaseIntegrationTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.hibernate.ddl-auto=none")
abstract class AbstractUserDataApiProviderPactTests extends BaseIntegrationTest {

    private static final String ACTOR_OID = "00000000-0000-0000-0000-000000000001";
    private static final String PACT_TOKEN = "pact-provider-test-token";

    @LocalServerPort
    private int port;

    @MockitoBean(name = "jwtDecoder")
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtToAuthenticatedEntraUserConverter userConverter;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));

        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue(PACT_TOKEN)
                .header("alg", "none")
                .claim("oid", ACTOR_OID)
                .claim("scp", "user_data.read")
                .build());
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @TargetRequestFilter
    void addAuthorizationHeader(HttpRequest request) {
        request.setHeader("Authorization", "Bearer " + PACT_TOKEN);
    }

    @State("the signed-in user has a profile")
    void signedInUserHasProfile() {
        var user = AuthenticatedEntraUser.builder()
                .entraOid(ACTOR_OID)
                .email("jane.doe@example.com")
                .firstName("Jane Doe")
                .build();
        when(userConverter.convert(any(Jwt.class))).thenReturn(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("SCOPE_user_data.read"))));
    }
}
