package uk.gov.justice.laa.datauserapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailResponse;
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

        ResponseEntity<UserProfileDetailResponse> response = controller.me(user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userProfileDetailView().userEntraObjectId()).isEqualTo(UUID.fromString(oid));
        assertThat(response.getBody().userProfileDetailView().email()).isEqualTo(email);
        assertThat(response.getBody().userProfileDetailView().activeProfile()).isFalse();
        assertThat(response.getBody().userProfileDetailView().hasAppRoles()).isFalse();
        assertThat(response.getBody().userProfileDetailView().unrestrictedOfficeAccess()).isFalse();
    }

    @Test
    void me_returns422_whenOidClaimMissing() {
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .build();

        ResponseEntity<UserProfileDetailResponse> response = controller.me(user);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNull();
    }
}
