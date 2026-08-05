package uk.gov.justice.laa.datauserapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleControllerTest {

    private final ExampleController controller = new ExampleController();

    @Test
    void me_returnsOidFromJwtClaim() {
        String oid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("oid", oid)
            .subject("test-sub")
            .issuedAt(Instant.now().minusSeconds(60))
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        ResponseEntity<Map<String, String>> response = controller.me(jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("oid", oid);
        assertThat(response.getBody()).containsEntry("sub", "test-sub");
    }

    @Test
    void me_returns422_whenOidClaimMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("no-oid-user")
            .issuedAt(Instant.now().minusSeconds(60))
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        ResponseEntity<Map<String, String>> response = controller.me(jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).containsKey("error");
    }
}
