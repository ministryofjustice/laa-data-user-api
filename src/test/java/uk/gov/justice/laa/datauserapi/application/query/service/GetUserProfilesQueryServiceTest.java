package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserProfilesQueryServiceTest {

    @Mock
    private UserProfileQueryRepository userProfileQueryRepository;

    private GetUserProfilesQueryService service;
    private String userEntraObjectId;

    @BeforeEach
    void setUp() {
        userEntraObjectId = UUID.randomUUID().toString();
        service = new GetUserProfilesQueryService(userProfileQueryRepository);
    }

    @Test
    void returnsProfilesWhenPresent() {
        UserProfileSummaryView profile = new UserProfileSummaryView(
                UUID.randomUUID(), true, UserType.INTERNAL, "user@example.com", "John Doe",
                "", "", false, UserAccountStatus.ACTIVE, UserProfileStatus.COMPLETE, true);
        List<UserProfileSummaryView> profiles = List.of(profile);

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(profiles));

        List<UserProfileSummaryView> result = service.getUserProfiles(userEntraObjectId);

        assertThat(result).isEqualTo(profiles);
    }

    @Test
    void returnsEmptyListWhenNoProfiles() {
        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.empty());

        List<UserProfileSummaryView> result = service.getUserProfiles(userEntraObjectId);

        assertThat(result).isEmpty();
    }
}
