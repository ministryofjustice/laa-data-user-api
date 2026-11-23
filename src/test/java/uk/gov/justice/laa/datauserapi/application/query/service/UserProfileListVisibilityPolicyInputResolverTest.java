package uk.gov.justice.laa.datauserapi.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfileListPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;

class UserProfileListVisibilityPolicyInputResolverTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    private final UserProfileQueryRepository repository = mock(UserProfileQueryRepository.class);
    private final UserProfileListVisibilityPolicyInputResolver resolver =
            new UserProfileListVisibilityPolicyInputResolver(repository);

    @Test
    void resolvesTargetFactsWithOneLookup() {
        String userEntraObjectId = UUID.randomUUID().toString();
        List<ProfileVisibilityContext> contexts =
                List.of(new ProfileVisibilityContext(true, UUID.randomUUID(), UserType.EXTERNAL, FIRM_ID));
        when(repository.findProfileVisibilityContexts(userEntraObjectId)).thenReturn(contexts);

        assertThat(resolver.resolve(new GetUserProfilesQuery(userEntraObjectId))).isEqualTo(contexts);
        verify(repository).findProfileVisibilityContexts(userEntraObjectId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void declaresItselfAsTheInputResolverForCanViewProfileListPolicy() {
        assertThat(resolver.policyType()).isEqualTo(CanViewProfileListPolicy.class);
        assertThat(resolver.messageType()).isEqualTo(GetUserProfilesQuery.class);
    }

    @Test
    void filterVisibleKeepsOnlyProfilesVisibleToTheActorWithoutAnyDbRead() {
        UUID visibleProfileId = UUID.randomUUID();
        UUID otherFirmId = UUID.randomUUID();
        UserProfileSummaryView visible = summary(visibleProfileId, UserType.EXTERNAL, FIRM_ID);
        UserProfileSummaryView notVisible = summary(UUID.randomUUID(), UserType.EXTERNAL, otherFirmId);

        AuthenticatedEntraUser actor = external(FIRM_ID, Permission.VIEW_EXTERNAL_USER);

        List<UserProfileSummaryView> result = resolver.filterVisible(List.of(visible, notVisible), actor);

        assertThat(result).containsExactly(visible);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void filterVisibleReturnsEmptyListWhenNoneAreVisible() {
        UserProfileSummaryView notVisible = summary(UUID.randomUUID(), UserType.INTERNAL, null);

        assertThat(resolver.filterVisible(List.of(notVisible), external(FIRM_ID))).isEmpty();
    }

    @Test
    void filterVisibleAlwaysKeepsTheActorsOwnProfile() {
        UUID ownProfileId = UUID.randomUUID();
        AuthenticatedEntraUser actor = actorWithOwnProfile(ownProfileId, FIRM_ID);
        UserProfileSummaryView own = summary(ownProfileId, UserType.EXTERNAL, FIRM_ID);

        assertThat(resolver.filterVisible(List.of(own), actor)).containsExactly(own);
    }

    private static UserProfileSummaryView summary(UUID profileId, UserType userType, UUID firmId) {
        return new UserProfileSummaryView(
                profileId, true, userType, "user@example.com", "John Doe", "Firm Name",
                firmId == null ? "" : firmId.toString(), false, UserAccountStatus.ACTIVE,
                UserProfileStatus.COMPLETE, true);
    }

    private static AuthenticatedEntraUser external(UUID firmId, Permission... permissions) {
        Set<AuthenticatedAppRole> roles = permissions.length == 0 ? Set.of()
                : Set.of(new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null, "Role",
                        true, null, Set.of(permissions)));
        return actor(AuthenticatedUserProfile.builder().id(UUID.randomUUID()).activeProfile(true)
                .userType(UserType.EXTERNAL).firmId(firmId).appRoles(roles).build());
    }

    private static AuthenticatedEntraUser actorWithOwnProfile(UUID profileId, UUID firmId) {
        return actor(AuthenticatedUserProfile.builder().id(profileId).activeProfile(true)
                .userType(UserType.EXTERNAL).firmId(firmId).appRoles(Set.of()).build());
    }

    private static AuthenticatedEntraUser actor(AuthenticatedUserProfile profile) {
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }
}
