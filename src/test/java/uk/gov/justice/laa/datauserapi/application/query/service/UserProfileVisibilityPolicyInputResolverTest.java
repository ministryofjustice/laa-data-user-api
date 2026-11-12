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
import uk.gov.justice.laa.datauserapi.application.query.dto.UserProfileAccessTarget;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess.GetUserProfileAppsQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileAccessQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

class UserProfileVisibilityPolicyInputResolverTest {

    private final UserProfileAccessQueryRepository repository = mock(UserProfileAccessQueryRepository.class);
    private final UserProfileVisibilityPolicyInputResolver resolver =
            new UserProfileVisibilityPolicyInputResolver(repository);

    @Test
    void resolvesTargetFactsWithOneLookup() {
        UUID profileId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        when(repository.findAccessTargetById(profileId))
                .thenReturn(Optional.of(new UserProfileAccessTarget(UserType.EXTERNAL, firmId, true)));

        ProfileVisibilityContext context = resolver.resolve(new GetUserProfileAppsQuery(profileId));

        assertThat(context).isEqualTo(new ProfileVisibilityContext(true, UserType.EXTERNAL, firmId));
        verify(repository).findAccessTargetById(profileId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void resolvesMissingProfile() {
        UUID profileId = UUID.randomUUID();
        when(repository.findAccessTargetById(profileId)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(new GetUserProfileAppsQuery(profileId)).profileExists()).isFalse();
    }
}
