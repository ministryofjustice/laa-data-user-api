package uk.gov.justice.laa.datauserapi.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfileDetailsPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

class UserProfileDetailsVisibilityPolicyInputResolverTest {

    private final UserProfileQueryRepository repository = mock(UserProfileQueryRepository.class);
    private final UserProfileDetailsVisibilityPolicyInputResolver resolver =
            new UserProfileDetailsVisibilityPolicyInputResolver(repository);

    @Test
    void resolvesTargetFactsWithOneLookup() {
        String userEntraObjectId = UUID.randomUUID().toString();
        UUID profileId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        when(repository.findActiveProfileVisibilityContext(userEntraObjectId))
                .thenReturn(Optional.of(new ProfileVisibilityContext(true, profileId, UserType.EXTERNAL, firmId)));

        ProfileVisibilityContext context = resolver.resolve(new GetActiveProfileQuery(userEntraObjectId));

        assertThat(context).isEqualTo(new ProfileVisibilityContext(true, profileId, UserType.EXTERNAL, firmId));
        verify(repository).findActiveProfileVisibilityContext(userEntraObjectId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void resolvesMissingProfileWithoutThrowing() {
        String userEntraObjectId = UUID.randomUUID().toString();
        when(repository.findActiveProfileVisibilityContext(userEntraObjectId)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(new GetActiveProfileQuery(userEntraObjectId)).profileExists()).isFalse();
    }

    @Test
    void declaresItselfAsTheInputResolverForCanViewProfileDetailsPolicy() {
        assertThat(resolver.policyType()).isEqualTo(CanViewProfileDetailsPolicy.class);
        assertThat(resolver.messageType())
                .isEqualTo(uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.UserProfileQuery.class);
    }
}
