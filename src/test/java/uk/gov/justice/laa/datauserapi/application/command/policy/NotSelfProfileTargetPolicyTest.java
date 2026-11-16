package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUserProfile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotSelfProfileTargetPolicyTest {

    @Mock
    private UserCommandService userCommandService;

    private final UUID profileId = UUID.randomUUID();
    private final TargetsUserProfile message = () -> profileId;
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid("OID-1").build();

    @Test
    void deniesProfileOfActorsOwnAccount() {
        when(userCommandService.findProfileEntraOid(profileId)).thenReturn(Optional.of("oid-1"));

        assertThat(policy().evaluate(message, actor).granted()).isFalse();
    }

    @Test
    void allowsProfileOfAnotherAccount() {
        when(userCommandService.findProfileEntraOid(profileId)).thenReturn(Optional.of("other"));

        assertThat(policy().evaluate(message, actor).granted()).isTrue();
    }

    @Test
    void allowsProfileWithoutAccount() {
        when(userCommandService.findProfileEntraOid(profileId)).thenReturn(Optional.empty());

        assertThat(policy().evaluate(message, actor).granted()).isTrue();
    }

    private NotSelfProfileTargetPolicy policy() {
        return new NotSelfProfileTargetPolicy(userCommandService);
    }
}
