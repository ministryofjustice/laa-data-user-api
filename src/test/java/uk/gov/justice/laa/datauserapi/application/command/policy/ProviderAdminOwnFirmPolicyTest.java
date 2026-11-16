package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsFirm;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderAdminOwnFirmPolicyTest {

    private final ProviderAdminOwnFirmPolicy policy = new ProviderAdminOwnFirmPolicy();
    private final TargetsFirm message = () -> "FIRM1";

    @Test
    void doesNotApplyToInternalActors() {
        assertThat(policy.appliesTo(message, actor(UserType.INTERNAL, null))).isFalse();
    }

    @Test
    void appliesToExternalActorsAndActorsWithoutProfile() {
        assertThat(policy.appliesTo(message, actor(UserType.EXTERNAL, "FIRM1"))).isTrue();
        assertThat(policy.appliesTo(message, AuthenticatedEntraUser.builder().build())).isTrue();
    }

    @Test
    void allowsOwnFirmIgnoringCase() {
        assertThat(policy.evaluate(message, actor(UserType.EXTERNAL, "firm1")).granted()).isTrue();
    }

    @Test
    void deniesOtherFirm() {
        assertThat(policy.evaluate(message, actor(UserType.EXTERNAL, "FIRM2")).granted()).isFalse();
    }

    @Test
    void deniesActorWithoutFirmOrProfile() {
        assertThat(policy.evaluate(message, actor(UserType.EXTERNAL, null)).granted()).isFalse();
        assertThat(policy.evaluate(message, AuthenticatedEntraUser.builder().build()).granted()).isFalse();
    }

    private static AuthenticatedEntraUser actor(UserType type, String firmCode) {
        return AuthenticatedEntraUser.builder()
                .activeProfile(AuthenticatedUserProfile.builder().userType(type).firmCode(firmCode).build())
                .build();
    }
}
