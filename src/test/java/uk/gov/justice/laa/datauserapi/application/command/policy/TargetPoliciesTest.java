package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TargetPoliciesTest {

    @Mock
    private UserCommandService userCommandService;

    private final UUID targetId = UUID.randomUUID();
    private final TargetsUser message = () -> targetId;
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
            .entraOid(UUID.randomUUID().toString()).build();

    @Test
    void notSelfPolicyDeniesOwnAccountOnly() {
        NotSelfTargetPolicy policy = new NotSelfTargetPolicy();
        AuthenticatedEntraUser self = AuthenticatedEntraUser.builder().entraOid(targetId.toString().toUpperCase()).build();

        assertThat(policy.evaluate(message, self).granted()).isFalse();
        assertThat(policy.evaluate(message, actor).granted()).isTrue();
        assertThat(policy.evaluate(() -> null, actor).granted()).isTrue();
    }

    @Test
    void externalTargetOnlyPolicyDeniesInternalTargets() {
        ExternalTargetOnlyPolicy policy = new ExternalTargetOnlyPolicy(userCommandService);

        when(userCommandService.isInternalUser(targetId)).thenReturn(true);
        assertThat(policy.evaluate(message, actor).granted()).isFalse();

        when(userCommandService.isInternalUser(targetId)).thenReturn(false);
        assertThat(policy.evaluate(message, actor).granted()).isTrue();
    }

    @Test
    void targetNotDeactivatedPolicyDeniesAlreadyDeactivatedTargets() {
        TargetNotDeactivatedPolicy policy = new TargetNotDeactivatedPolicy(userCommandService);

        when(userCommandService.isDeactivated(targetId)).thenReturn(true);
        assertThat(policy.evaluate(message, actor).granted()).isFalse();

        when(userCommandService.isDeactivated(targetId)).thenReturn(false);
        assertThat(policy.evaluate(message, actor).granted()).isTrue();
    }
}
