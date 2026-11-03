package uk.gov.justice.laa.datauserapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleControllerTest {

    private final ExampleController controller = new ExampleController();

    @Test
    void me_returnsOidFromJwtClaim() {
        String oid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String email = "test@example.com";
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(oid)
                .email(email)
                .build();

        ResponseEntity<UserProfileDetailView> response = controller.me(user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userEntraObjectId()).isEqualTo(UUID.fromString(oid));
        assertThat(response.getBody().email()).isEqualTo(email);
        assertThat(response.getBody().activeProfile()).isFalse();
        assertThat(response.getBody().hasAppRoles()).isFalse();
        assertThat(response.getBody().unrestrictedOfficeAccess()).isFalse();
    }

    @Test
    void me_returns422_whenOidClaimMissing() {
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .build();

        ResponseEntity<UserProfileDetailView> response = controller.me(user);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNull();
    }
}
