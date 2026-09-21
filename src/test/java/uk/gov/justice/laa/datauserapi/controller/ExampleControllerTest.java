package uk.gov.justice.laa.datauserapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleControllerTest {

    private final ExampleController controller = new ExampleController();

    @Test
    void me_returnsOidFromJwtClaim() {
        String oid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String email = "test@example.com";
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("oid", oid)
            .claim("email", email)
            .subject("test-sub")
            .issuedAt(Instant.now().minusSeconds(60))
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        ResponseEntity<UserProfileDetailResponse> response = controller.me(jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userEntraObjectId()).isEqualTo(oid);
        assertThat(response.getBody().email()).isEqualTo(email);
        assertThat(response.getBody().activeProfile()).isFalse();
        assertThat(response.getBody().hasAppRoles()).isFalse();
        assertThat(response.getBody().unrestrictedOfficeAccess()).isFalse();
    }

    @Test
    void me_returns422_whenOidClaimMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("no-oid-user")
            .issuedAt(Instant.now().minusSeconds(60))
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        ResponseEntity<UserProfileDetailResponse> response = controller.me(jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNull();
    }
}
