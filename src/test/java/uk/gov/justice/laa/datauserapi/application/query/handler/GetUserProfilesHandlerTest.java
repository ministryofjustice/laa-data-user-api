package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserProfilesHandlerTest {

    @Mock
    private UserProfileQueryRepository userProfileQueryRepository;

    private String userEntraObjectId;
    private String actorOid;

    @BeforeEach
    void setUp() {
        userEntraObjectId = UUID.randomUUID().toString();
        actorOid = UUID.randomUUID().toString();
    }

    @Test
    void delegatesToProfileService() {
        UserProfileDetailsService service = mock(UserProfileDetailsService.class);
        AuthenticatedEntraUser actor = authenticatedTestUser();
        List<UserProfileSummaryView> expected = List.of(mock(UserProfileSummaryView.class));
        when(service.getProfiles(userEntraObjectId, actor)).thenReturn(expected);

        GetUserProfilesQuery query = new GetUserProfilesQuery(userEntraObjectId);
        assertThat(new GetUserProfilesHandler(service).handle(query, actor)).isSameAs(expected);
        verify(service).getProfiles(userEntraObjectId, actor);
    }

    @Test
    void returnsEmptyListWhenRepositoryHasNoResult() {
        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.empty());

        GetUserProfilesHandler handler = handler();
        GetUserProfilesQuery query = new GetUserProfilesQuery(userEntraObjectId);
        List<UserProfileSummaryView> result = handler.handle(query, authenticatedTestUser());

        assertThat(result).isEmpty();
    }

    @Test
    void returnsAllProfilesWhenFound() {
        UUID profileId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        UserProfileSummaryView profile = new UserProfileSummaryView(
                profileId, true, UserType.EXTERNAL, "user@example.com", "John Doe",
                "Firm Name", firmId.toString(), false, UserAccountStatus.ACTIVE, UserProfileStatus.COMPLETE, true);

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(List.of(profile)));

        GetUserProfilesHandler handler = handler();
        GetUserProfilesQuery query = new GetUserProfilesQuery(userEntraObjectId);
        AuthenticatedEntraUser actor = authenticatedTestUserWithFirm(profileId, firmId);
        List<UserProfileSummaryView> result = handler.handle(query, actor);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().userProfileId()).isEqualTo(profileId);
    }

    private GetUserProfilesHandler handler() {
        return new GetUserProfilesHandler(new UserProfileDetailsService(
                userProfileQueryRepository,
                mock(UserProfileQueryMapper.class),
                mock(GetActiveProfileQueryMapper.class)));
    }

    private AuthenticatedEntraUser authenticatedTestUser() {
        UUID profileId = UUID.randomUUID();
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(profileId)
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firmId(UUID.randomUUID())
                .build();
        return AuthenticatedEntraUser.builder()
                .entraOid(actorOid)
                .activeProfile(profile)
                .build();
    }

    private AuthenticatedEntraUser authenticatedTestUserWithFirm(UUID profileId, UUID firmId) {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(profileId)
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firmId(firmId)
                .build();
        return AuthenticatedEntraUser.builder()
                .entraOid(actorOid)
                .activeProfile(profile)
                .build();
    }
}
