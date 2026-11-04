package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUserProfile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalProfileTargetOnlyPolicyTest {

    @Mock
    private UserCommandService userCommandService;

    private final UUID profileId = UUID.randomUUID();
    private final TargetsUserProfile message = () -> profileId;
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();

    @Test
    void deniesInternalProfile() {
        when(userCommandService.findProfileUserType(profileId)).thenReturn(Optional.of(UserType.INTERNAL));

        assertThat(policy().evaluate(message, actor).granted()).isFalse();
    }

    @Test
    void allowsExternalProfile() {
        when(userCommandService.findProfileUserType(profileId)).thenReturn(Optional.of(UserType.EXTERNAL));

        assertThat(policy().evaluate(message, actor).granted()).isTrue();
    }

    private ExternalProfileTargetOnlyPolicy policy() {
        return new ExternalProfileTargetOnlyPolicy(userCommandService);
    }
}
