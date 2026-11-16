package uk.gov.justice.laa.datauserapi.application.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.MissingOidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileServiceTest {

    private final UserProfileService service = new UserProfileService();
    private final String oid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test
    void mapsCallerIdentityAndPreservesProfileDefaults() {
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(oid).email("test@example.com").firstName("Test").build();

        var view = service.getProfile(user);

        assertThat(view.userEntraObjectId()).isEqualTo(UUID.fromString(oid));
        assertThat(view.email()).isEqualTo("test@example.com");
        assertThat(view.fullName()).isEqualTo("Test");
        assertThat(view.userType()).isEqualTo(UserType.EXTERNAL);
        assertThat(view.activeProfile()).isFalse();
        assertThat(view.hasAppRoles()).isFalse();
        assertThat(view.unrestrictedOfficeAccess()).isFalse();
    }

    @Test
    void suppliesFallbacksForMissingOptionalClaims() {
        var view = service.getProfile(AuthenticatedEntraUser.builder().entraOid(oid).build());

        assertThat(view.email()).isEmpty();
        assertThat(view.fullName()).isEqualTo("fullName");
    }

    @Test
    void throwsWhenCallerOrOidIsMissing() {
        assertThatThrownBy(() -> service.getProfile(null)).isInstanceOf(MissingOidException.class);
        assertThatThrownBy(() -> service.getProfile(AuthenticatedEntraUser.builder().build()))
                .isInstanceOf(MissingOidException.class);
        assertThatThrownBy(() -> service.getProfile(AuthenticatedEntraUser.builder().entraOid(" ").build()))
                .isInstanceOf(MissingOidException.class);
    }

    @Test
    void preservesFailureForMalformedOid() {
        assertThatThrownBy(() -> service.getProfile(AuthenticatedEntraUser.builder().entraOid("invalid").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
