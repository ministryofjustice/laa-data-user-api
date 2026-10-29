package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderAdminFirmScopePolicyTest {

    @Mock
    private UserCommandService userCommandService;

    private final UUID targetId = UUID.randomUUID();
    private final TargetsUser message = () -> targetId;

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
    void allowsTargetInSameFirm() {
        UUID firmId = UUID.randomUUID();
        when(userCommandService.isMultiFirmUser(targetId)).thenReturn(false);
        when(userCommandService.findActiveFirmId(targetId)).thenReturn(Optional.of(firmId));

        assertThat(policy().evaluate(message, actor(UserType.EXTERNAL, firmId)).granted()).isTrue();
    }

    @Test
    void deniesTargetInDifferentFirm() {
        when(userCommandService.isMultiFirmUser(targetId)).thenReturn(false);
        when(userCommandService.findActiveFirmId(targetId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThat(policy().evaluate(message, actor(UserType.EXTERNAL, UUID.randomUUID())).granted()).isFalse();
    }

    @Test
    void deniesTargetWithoutFirm() {
        when(userCommandService.isMultiFirmUser(targetId)).thenReturn(false);
        when(userCommandService.findActiveFirmId(targetId)).thenReturn(Optional.empty());

        assertThat(policy().evaluate(message, actor(UserType.EXTERNAL, UUID.randomUUID())).granted()).isFalse();
    }

    @Test
    void deniesMultiFirmTarget() {
        when(userCommandService.isMultiFirmUser(targetId)).thenReturn(true);

        assertThat(policy().evaluate(message, actor(UserType.EXTERNAL, UUID.randomUUID())).granted()).isFalse();
    }

    @Test
    void deniesActorWithoutFirmOrProfile() {
        assertThat(policy().evaluate(message, actor(UserType.EXTERNAL, null)).granted()).isFalse();
        assertThat(policy().evaluate(message, AuthenticatedEntraUser.builder().build()).granted()).isFalse();
        verifyNoInteractions(userCommandService);
    }

    private ProviderAdminFirmScopePolicy policy() {
        return new ProviderAdminFirmScopePolicy(userCommandService);
    }

    private static AuthenticatedEntraUser actor(UserType type, UUID firmId) {
        return AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .activeProfile(AuthenticatedUserProfile.builder().activeProfile(true).userType(type).firmId(firmId).build())
                .build();
    }
}
