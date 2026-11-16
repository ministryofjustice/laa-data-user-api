package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUserProfile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderAdminProfileFirmScopePolicyTest {

    @Mock
    private UserCommandService userCommandService;

    private final UUID profileId = UUID.randomUUID();
    private final TargetsUserProfile message = () -> profileId;

    @Test
    void doesNotApplyToInternalActors() {
        assertThat(policy().appliesTo(message, actor(UserType.INTERNAL, null))).isFalse();
    }

    @Test
    void appliesToExternalActorsAndActorsWithoutProfile() {
        assertThat(policy().appliesTo(message, actor(UserType.EXTERNAL, UUID.randomUUID()))).isTrue();
        assertThat(policy().appliesTo(message, AuthenticatedEntraUser.builder().build())).isTrue();
    }

    @Test
    void allowsProfileInSameFirm() {
        UUID firmId = UUID.randomUUID();
        when(userCommandService.findProfileFirmId(profileId)).thenReturn(Optional.of(firmId));

        assertThat(policy().evaluate(message, actor(UserType.EXTERNAL, firmId)).granted()).isTrue();
    }

    @Test
    void deniesProfileInDifferentFirmOrWithoutFirm() {
        when(userCommandService.findProfileFirmId(profileId))
                .thenReturn(Optional.of(UUID.randomUUID()), Optional.empty());

        assertThat(policy().evaluate(message, actor(UserType.EXTERNAL, UUID.randomUUID())).granted()).isFalse();
        assertThat(policy().evaluate(message, actor(UserType.EXTERNAL, UUID.randomUUID())).granted()).isFalse();
    }

    @Test
    void deniesActorWithoutFirmWithoutLookup() {
        assertThat(policy().evaluate(message, actor(UserType.EXTERNAL, null)).granted()).isFalse();
        verifyNoInteractions(userCommandService);
    }

    private ProviderAdminProfileFirmScopePolicy policy() {
        return new ProviderAdminProfileFirmScopePolicy(userCommandService);
    }

    private static AuthenticatedEntraUser actor(UserType type, UUID firmId) {
        return AuthenticatedEntraUser.builder()
                .activeProfile(AuthenticatedUserProfile.builder().userType(type).firmId(firmId).build())
                .build();
    }
}
