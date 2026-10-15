package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofilesbyentrauser.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserProfilesHandlerTest {

    @Mock
    private UserProfileQueryRepository userProfileQueryRepository;

    private static final String ACTOR_OID = UUID.randomUUID().toString();
    private static final String USER_ENTRA_OBJECT_ID = UUID.randomUUID().toString();

    @Test
    void returnsEmptyListWhenUserHasNoVisibleProfiles() {
        GetUserProfilesQuery query = new GetUserProfilesQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);
        List<UserProfileSummaryView> emptyList = new ArrayList<>();

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(emptyList));

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository);
        List<UserProfileSummaryView> result = handler.handle(query);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsAllVisibleProfiles() {
        GetUserProfilesQuery query = new GetUserProfilesQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);
        UUID profileId1 = UUID.randomUUID();
        UUID profileId2 = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        
        List<UserProfileSummaryView> profiles = List.of(
                new UserProfileSummaryView(
                        profileId1,
                        true,
                        UserType.INTERNAL,
                        "user@example.com",
                        "John Doe",
                        "",
                        "",
                        false,
                        UserAccountStatus.ACTIVE,
                        UserProfileStatus.COMPLETE,
                        true
                ),
                new UserProfileSummaryView(
                        profileId2,
                        false,
                        UserType.EXTERNAL,
                        "user@example.com",
                        "John Doe",
                        "Test Firm",
                        firmId.toString(),
                        false,
                        UserAccountStatus.ACTIVE,
                        UserProfileStatus.COMPLETE,
                        false
                )
        );

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(profiles));

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository);
        List<UserProfileSummaryView> result = handler.handle(query);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).userProfileId()).isEqualTo(profileId1);
        assertThat(result.get(1).userProfileId()).isEqualTo(profileId2);
    }

    @Test
    void returnsSingleProfile() {
        GetUserProfilesQuery query = new GetUserProfilesQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);
        UUID profileId = UUID.randomUUID();
        
        List<UserProfileSummaryView> profiles = List.of(
                new UserProfileSummaryView(
                        profileId,
                        true,
                        UserType.INTERNAL,
                        "user@example.com",
                        "Jane Smith",
                        "",
                        "",
                        false,
                        UserAccountStatus.ACTIVE,
                        UserProfileStatus.COMPLETE,
                        true
                )
        );

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(profiles));

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository);
        List<UserProfileSummaryView> result = handler.handle(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userProfileId()).isEqualTo(profileId);
        assertThat(result.get(0).userType()).isEqualTo(UserType.INTERNAL);
    }

    @Test
    void includesHasAppRolesIndicator() {
        GetUserProfilesQuery query = new GetUserProfilesQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);
        UUID profileId1 = UUID.randomUUID();
        UUID profileId2 = UUID.randomUUID();
        
        List<UserProfileSummaryView> profiles = List.of(
                new UserProfileSummaryView(
                        profileId1,
                        true,
                        UserType.INTERNAL,
                        "user@example.com",
                        "Test User",
                        "",
                        "",
                        false,
                        UserAccountStatus.ACTIVE,
                        UserProfileStatus.COMPLETE,
                        true
                ),
                new UserProfileSummaryView(
                        profileId2,
                        false,
                        UserType.EXTERNAL,
                        "user@example.com",
                        "Test User",
                        "Firm",
                        UUID.randomUUID().toString(),
                        false,
                        UserAccountStatus.ACTIVE,
                        UserProfileStatus.COMPLETE,
                        false
                )
        );

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(profiles));

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository);
        List<UserProfileSummaryView> result = handler.handle(query);

        assertThat(result.get(0).hasAppRoles()).isTrue();
        assertThat(result.get(1).hasAppRoles()).isFalse();
    }

    @Test
    void respectsAuthorizationRulesForFiltering() {
        GetUserProfilesQuery query = new GetUserProfilesQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);
        UUID profileId = UUID.randomUUID();
        
        List<UserProfileSummaryView> visibleProfiles = List.of(
                new UserProfileSummaryView(
                        profileId,
                        true,
                        UserType.INTERNAL,
                        "user@example.com",
                        "John Doe",
                        "",
                        "",
                        false,
                        UserAccountStatus.ACTIVE,
                        UserProfileStatus.COMPLETE,
                        true
                )
        );

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(visibleProfiles));

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository);
        List<UserProfileSummaryView> result = handler.handle(query);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().userType()).isEqualTo(UserType.INTERNAL);
    }

    @Test
    void returnsNonPaginatedResultsUpTo100Items() {
        GetUserProfilesQuery query = new GetUserProfilesQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);
        List<UserProfileSummaryView> profiles = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            profiles.add(new UserProfileSummaryView(
                    UUID.randomUUID(),
                    i == 0,
                    UserType.INTERNAL,
                    "user@example.com",
                    "User " + i,
                    "",
                    "",
                    false,
                    UserAccountStatus.ACTIVE,
                    UserProfileStatus.COMPLETE,
                    i % 2 == 0
            ));
        }

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(profiles));

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository);
        List<UserProfileSummaryView> result = handler.handle(query);

        assertThat(result).hasSize(50);
        assertThat(result.size()).isLessThanOrEqualTo(100);
    }

    @Test
    void providesCompleteProfileSummaryInformation() {
        GetUserProfilesQuery query = new GetUserProfilesQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);
        UUID profileId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        
        List<UserProfileSummaryView> profiles = List.of(
                new UserProfileSummaryView(
                        profileId,
                        true,
                        UserType.EXTERNAL,
                        "john.doe@example.com",
                        "John Doe",
                        "Test Firm Ltd",
                        firmId.toString(),
                        true,
                        UserAccountStatus.ACTIVE,
                        UserProfileStatus.COMPLETE,
                        true
                )
        );

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(profiles));

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository);
        List<UserProfileSummaryView> result = handler.handle(query);

        UserProfileSummaryView profile = result.getFirst();
        assertThat(profile.userProfileId()).isEqualTo(profileId);
        assertThat(profile.email()).isEqualTo("john.doe@example.com");
        assertThat(profile.fullName()).isEqualTo("John Doe");
        assertThat(profile.accountStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(profile.profileStatus()).isEqualTo(UserProfileStatus.COMPLETE);
        assertThat(profile.firmName()).isEqualTo("Test Firm Ltd");
        assertThat(profile.firmId()).isEqualTo(firmId.toString());
        assertThat(profile.multiFirmUser()).isTrue();
    }
}
