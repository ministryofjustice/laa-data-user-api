package uk.gov.justice.laa.datauserapi.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileSilasStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;
import uk.gov.justice.laa.datauserapi.model.Permission;

@ExtendWith(MockitoExtension.class)
class UserProfileDetailsServiceTest {

    private static final UUID FIRM_A_ID = UUID.randomUUID();
    private static final UUID FIRM_B_ID = UUID.randomUUID();

    @Mock
    private UserProfileQueryRepository repository;

    @Mock
    private UserProfileQueryMapper queryMapper;

    @Mock
    private GetActiveProfileQueryMapper viewMapper;

    @Mock
    private UserProfileListVisibilityPolicyInputResolver listVisibilityResolver;

    private UserProfileDetailsService serviceUnderTest() {
        return new UserProfileDetailsService(repository, queryMapper, viewMapper, listVisibilityResolver);
    }

    @Test
    void getActiveProfileReturnsTheDetailViewWhenTheLoadedProfileIsVisibleToTheActor() {
        String userEntraObjectId = UUID.randomUUID().toString();
        UUID profileId = UUID.randomUUID();
        AuthenticatedEntraUser actor = external(profileId, FIRM_A_ID, Permission.VIEW_EXTERNAL_USER);

        Optional<UserProfile> loadedProfile = Optional.of(mock(UserProfile.class));
        UserProfileDto dto = mock(UserProfileDto.class);
        UserProfileQueryMapper.ActiveProfileLookup lookup = new UserProfileQueryMapper.ActiveProfileLookup(
                dto, new ProfileVisibilityContext(true, profileId, UserType.EXTERNAL, FIRM_A_ID));
        UserProfileDetailView expectedView = mock(UserProfileDetailView.class);

        when(repository.findActiveProfileWithDetailsAndRoles(userEntraObjectId)).thenReturn(loadedProfile);
        when(queryMapper.toActiveProfileLookup(loadedProfile)).thenReturn(Optional.of(lookup));
        when(viewMapper.mapProfileToDetailView(dto)).thenReturn(expectedView);

        UserProfileDetailView result = serviceUnderTest().getActiveProfile(userEntraObjectId, actor);

        assertThat(result).isSameAs(expectedView);
    }

    @Test
    void getActiveProfileThrowsNotFoundWhenNoActiveProfileExists() {
        String userEntraObjectId = UUID.randomUUID().toString();
        AuthenticatedEntraUser actor = external(UUID.randomUUID(), FIRM_A_ID, Permission.VIEW_EXTERNAL_USER);

        when(repository.findActiveProfileWithDetailsAndRoles(userEntraObjectId)).thenReturn(Optional.empty());
        when(queryMapper.toActiveProfileLookup(Optional.empty())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceUnderTest().getActiveProfile(userEntraObjectId, actor))
                .isInstanceOf(UserProfileNotFoundException.class);

        verifyNoInteractions(viewMapper);
    }

    /**
     * Regression test for the profile-switch race: the RBAC interceptor authorises a request against one read
     * of the target's active-profile facts (e.g. Firm A, taken via {@code UserProfileDetailsVisibilityPolicyInputResolver}),
     * but the target can switch their active profile to a different firm before the handler's own read runs.
     * The service must re-check visibility against the profile it actually loaded, not trust the interceptor's
     * earlier decision, so an actor authorised only for Firm A can never receive a Firm B profile even though the
     * interceptor originally granted access based on stale Firm A facts.
     */
    @Test
    void deniesAccessWhenTheActiveProfileHasSwitchedFirmsSinceTheRbacInterceptorsEarlierRead() {
        String userEntraObjectId = UUID.randomUUID().toString();
        UUID switchedFirmProfileId = UUID.randomUUID();
        // Actor was authorised by the interceptor based on an earlier read showing Firm A as active, and only
        // holds permission to view their own Firm A colleagues - not Firm B.
        AuthenticatedEntraUser actor = external(UUID.randomUUID(), FIRM_A_ID, Permission.VIEW_EXTERNAL_USER);

        // By the time the service runs its own single read, the target has switched their active profile to Firm B.
        Optional<UserProfile> loadedProfile = Optional.of(mock(UserProfile.class));
        UserProfileDto dto = mock(UserProfileDto.class);
        UserProfileQueryMapper.ActiveProfileLookup lookup = new UserProfileQueryMapper.ActiveProfileLookup(
                dto, new ProfileVisibilityContext(true, switchedFirmProfileId, UserType.EXTERNAL, FIRM_B_ID));

        when(repository.findActiveProfileWithDetailsAndRoles(userEntraObjectId)).thenReturn(loadedProfile);
        when(queryMapper.toActiveProfileLookup(loadedProfile)).thenReturn(Optional.of(lookup));

        assertThatThrownBy(() -> serviceUnderTest().getActiveProfile(userEntraObjectId, actor))
                .isInstanceOf(UserProfileNotFoundException.class);

        verifyNoInteractions(viewMapper);
    }

    @Test
    void getProfilesDelegatesFilteringToTheListVisibilityResolverWithoutFilteringItself() {
        String userEntraObjectId = UUID.randomUUID().toString();
        AuthenticatedEntraUser actor = external(UUID.randomUUID(), FIRM_A_ID, Permission.VIEW_EXTERNAL_USER);

        List<UserProfileSummaryView> allProfiles = List.of(summary(UUID.randomUUID(), FIRM_A_ID));
        List<UserProfileSummaryView> visibleProfiles = List.of(allProfiles.getFirst());

        when(repository.findAllProfilesByUserEntraObjectId(userEntraObjectId)).thenReturn(Optional.of(allProfiles));
        when(listVisibilityResolver.filterVisible(allProfiles, actor)).thenReturn(visibleProfiles);

        List<UserProfileSummaryView> result = serviceUnderTest().getProfiles(userEntraObjectId, actor);

        assertThat(result).isSameAs(visibleProfiles);
        verify(listVisibilityResolver).filterVisible(allProfiles, actor);
    }

    @Test
    void getProfilesReturnsEmptyListWhenTheRepositoryHasNoResultWithoutCallingTheResolver() {
        String userEntraObjectId = UUID.randomUUID().toString();
        AuthenticatedEntraUser actor = external(UUID.randomUUID(), FIRM_A_ID, Permission.VIEW_EXTERNAL_USER);

        when(repository.findAllProfilesByUserEntraObjectId(userEntraObjectId)).thenReturn(Optional.empty());
        when(listVisibilityResolver.filterVisible(List.of(), actor)).thenReturn(List.of());

        assertThat(serviceUnderTest().getProfiles(userEntraObjectId, actor)).isEmpty();
    }

    private static UserProfileSummaryView summary(UUID profileId, UUID firmId) {
        return new UserProfileSummaryView(
                profileId, true, UserType.EXTERNAL, "user@example.com", "John Doe", "Firm Name",
                firmId.toString(), false, UserAccountStatus.ACTIVE, UserProfileSilasStatus.COMPLETE, true);
    }

    private static AuthenticatedEntraUser external(UUID profileId, UUID firmId, Permission... permissions) {
        Set<AuthenticatedAppRole> roles = permissions.length == 0 ? Set.of()
                : Set.of(new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null, "Role",
                        true, null, Set.of(permissions)));
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(profileId).activeProfile(true).userType(UserType.EXTERNAL).firmId(firmId).appRoles(roles).build();
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }
}
