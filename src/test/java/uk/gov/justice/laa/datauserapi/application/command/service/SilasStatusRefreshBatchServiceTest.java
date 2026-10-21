package uk.gov.justice.laa.datauserapi.application.command.service;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.SilasAccountStatus;
import uk.gov.justice.laa.datauserapi.model.UserProfileSilasStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SilasStatusRefreshBatchServiceTest {
    private static final String ACTOR = "refresh-actor";

    @Test
    void refreshBatch_calculatesAccountStatusesForEveryInvitationStateAndProfileType() {
        List<InvitationStatus> invitationStatuses = new ArrayList<>();
        invitationStatuses.add(null);
        invitationStatuses.addAll(List.of(InvitationStatus.values()));

        for (InvitationStatus invitationStatus : invitationStatuses) {
            for (UserType type : UserType.values()) {
                for (boolean active : List.of(false, true)) {
                    EntraUser user = user(invitationStatus, active, SilasAccountStatus.UNKNOWN);
                    UserProfile profile = profile(user, type, true, true, false, UserProfileSilasStatus.UNKNOWN);
                    Fixture fixture = fixture(List.of(user.getId()), List.of(user), List.of(profile), 7);

                    SilasStatusRefreshBatchService.BatchResult result = fixture.service.refreshBatch(null, ACTOR);

                    boolean internal = type == UserType.INTERNAL;
                    SilasAccountStatus expected = !internal && invitationStatus != InvitationStatus.VERIFICATION_SUCCESS
                            ? SilasAccountStatus.ACTIVATION_REQUIRED
                            : active ? SilasAccountStatus.ACTIVE : SilasAccountStatus.DEACTIVATED;
                    assertThat(user.getSilasAccountStatus()).isEqualTo(expected);
                    assertThat(result.updatedAccounts()).isEqualTo(1);
                }
            }
        }
    }

    @Test
    void refreshBatch_calculatesProfileStatusesForRolesOfficesInvitationAndUnrestrictedAccess() {
        List<InvitationStatus> invitationStatuses = new ArrayList<>();
        invitationStatuses.add(null);
        invitationStatuses.addAll(List.of(InvitationStatus.values()));

        for (InvitationStatus invitationStatus : invitationStatuses) {
            for (UserType type : UserType.values()) {
                for (boolean hasRoles : List.of(false, true)) {
                    for (boolean hasOffices : List.of(false, true)) {
                        for (boolean unrestricted : List.of(false, true)) {
                            EntraUser user = user(invitationStatus, true, SilasAccountStatus.ACTIVE);
                            UserProfile profile = profile(user, type, hasRoles, hasOffices, unrestricted,
                                    UserProfileSilasStatus.UNKNOWN);
                            Fixture fixture = fixture(List.of(user.getId()), List.of(user), List.of(profile), 4);
                            SilasStatusRefreshBatchService.BatchResult result = fixture.service.refreshBatch(null, ACTOR);

                            UserProfileSilasStatus expected;
                            if (type != UserType.INTERNAL
                                    && invitationStatus != InvitationStatus.VERIFICATION_SUCCESS) {
                                expected = UserProfileSilasStatus.ACTIVATION_REQUIRED;
                            } else if (!hasRoles || (type != UserType.INTERNAL && !unrestricted && !hasOffices)) {
                                expected = UserProfileSilasStatus.NO_ACCESS_ASSIGNED;
                            } else {
                                expected = UserProfileSilasStatus.COMPLETE;
                            }
                            assertThat(profile.getSilasStatus()).isEqualTo(expected);
                            assertThat(result.updatedProfiles()).isEqualTo(1);
                        }
                    }
                }
            }
        }
    }

    @Test
    void refreshBatch_handlesMixedUsersProfilesAndUsersWithoutProfiles() {
        EntraUser internal = user(InvitationStatus.INVITE_SENT, true, SilasAccountStatus.UNKNOWN);
        EntraUser externalUnverified = user(InvitationStatus.INVITE_SENT, false, SilasAccountStatus.UNKNOWN);
        EntraUser externalVerified = user(InvitationStatus.VERIFICATION_SUCCESS, true, SilasAccountStatus.UNKNOWN);
        UserProfile internalProfile = profile(internal, UserType.INTERNAL, true, false, false,
                UserProfileSilasStatus.UNKNOWN);
        UserProfile externalProfile = profile(externalUnverified, UserType.EXTERNAL, true, true, false,
                UserProfileSilasStatus.UNKNOWN);
        UserProfile secondExternalProfile = profile(externalVerified, UserType.EXTERNAL, true, false, true,
                UserProfileSilasStatus.UNKNOWN);
        Fixture fixture = fixture(
                List.of(internal.getId(), externalUnverified.getId(), externalVerified.getId()),
                List.of(internal, externalUnverified, externalVerified),
                List.of(internalProfile, externalProfile, secondExternalProfile), 8);

        SilasStatusRefreshBatchService.BatchResult result = fixture.service.refreshBatch(null, ACTOR);

        assertThat(internal.getSilasAccountStatus()).isEqualTo(SilasAccountStatus.ACTIVE);
        assertThat(externalUnverified.getSilasAccountStatus()).isEqualTo(SilasAccountStatus.ACTIVATION_REQUIRED);
        assertThat(externalVerified.getSilasAccountStatus()).isEqualTo(SilasAccountStatus.ACTIVE);
        assertThat(internalProfile.getSilasStatus()).isEqualTo(UserProfileSilasStatus.COMPLETE);
        assertThat(externalProfile.getSilasStatus()).isEqualTo(UserProfileSilasStatus.ACTIVATION_REQUIRED);
        assertThat(secondExternalProfile.getSilasStatus()).isEqualTo(UserProfileSilasStatus.COMPLETE);
        assertThat(result.updatedAccounts()).isEqualTo(3);
        assertThat(result.updatedProfiles()).isEqualTo(3);
    }

    @Test
    void refreshBatch_returnsNullCursorForEmptyFirstAndSubsequentPagesWithoutFetchingEntities() {
        Fixture first = fixture(List.of(), List.of(), List.of(), 3);
        assertThat(first.service.refreshBatch(null, ACTOR))
                .isEqualTo(new SilasStatusRefreshBatchService.BatchResult(null, 0, 0));
        verify(first.users).findRefreshUserIds(PageRequest.of(0, 3));
        verifyNoInteractions(first.profiles, first.entityManager);

        UUID cursor = UUID.randomUUID();
        Fixture subsequent = fixture(List.of(), List.of(), List.of(), 5);
        assertThat(subsequent.service.refreshBatch(cursor, ACTOR))
                .isEqualTo(new SilasStatusRefreshBatchService.BatchResult(null, 0, 0));
        verify(subsequent.users).findRefreshUserIdsAfter(cursor, PageRequest.of(0, 5));
        verifyNoInteractions(subsequent.profiles, subsequent.entityManager);
    }

    @Test
    void refreshBatch_usesLastSelectedIdAsCursorEvenWhenLockedUserWasDeleted() {
        UUID firstId = UUID.randomUUID();
        UUID deletedUserId = UUID.randomUUID();
        EntraUser remainingUser = user(InvitationStatus.VERIFICATION_SUCCESS, true, SilasAccountStatus.ACTIVE);
        remainingUser.setId(firstId);
        Fixture fixture = fixture(List.of(firstId, deletedUserId), List.of(remainingUser), List.of(), 2);

        SilasStatusRefreshBatchService.BatchResult result = fixture.service.refreshBatch(null, ACTOR);

        assertThat(result.lastUserId()).isEqualTo(deletedUserId);
        assertThat(result.updatedAccounts()).isZero();
        assertThat(result.updatedProfiles()).isZero();
    }

    @Test
    void refreshBatch_updatesOnlyChangedEntitiesAndSetsAuditFieldsForChanges() {
        EntraUser changedUser = user(InvitationStatus.VERIFICATION_SUCCESS, false, SilasAccountStatus.ACTIVE);
        EntraUser unchangedUser = user(InvitationStatus.VERIFICATION_SUCCESS, true, SilasAccountStatus.ACTIVE);
        LocalDateTime oldModified = LocalDateTime.of(2020, 1, 2, 3, 4);
        unchangedUser.setLastModified(oldModified);
        unchangedUser.setLastModifiedBy("old-actor");
        UserProfile changedProfile = profile(changedUser, UserType.EXTERNAL, true, true, false,
                UserProfileSilasStatus.NO_ACCESS_ASSIGNED);
        UserProfile unchangedProfile = profile(unchangedUser, UserType.EXTERNAL, true, true, false,
                UserProfileSilasStatus.COMPLETE);
        unchangedProfile.setLastModified(oldModified);
        unchangedProfile.setLastModifiedBy("old-actor");
        Fixture fixture = fixture(List.of(changedUser.getId(), unchangedUser.getId()),
                List.of(changedUser, unchangedUser), List.of(changedProfile, unchangedProfile), 10);

        SilasStatusRefreshBatchService.BatchResult result = fixture.service.refreshBatch(null, ACTOR);

        assertThat(changedUser.getSilasAccountStatus()).isEqualTo(SilasAccountStatus.DEACTIVATED);
        assertThat(changedUser.getLastModifiedBy()).isEqualTo(ACTOR);
        assertThat(changedUser.getLastModified()).isNotNull().isAfter(oldModified);
        assertThat(unchangedUser.getLastModifiedBy()).isEqualTo("old-actor");
        assertThat(unchangedUser.getLastModified()).isEqualTo(oldModified);
        assertThat(changedProfile.getSilasStatus()).isEqualTo(UserProfileSilasStatus.COMPLETE);
        assertThat(changedProfile.getLastModifiedBy()).isEqualTo(ACTOR);
        assertThat(changedProfile.getLastModified()).isNotNull().isAfter(oldModified);
        assertThat(unchangedProfile.getLastModifiedBy()).isEqualTo("old-actor");
        assertThat(unchangedProfile.getLastModified()).isEqualTo(oldModified);
        assertThat(result.updatedAccounts()).isEqualTo(1);
        assertThat(result.updatedProfiles()).isEqualTo(1);
    }

    @Test
    void refreshBatch_setsJdbcBatchSizeAndFetchesLocksThenCollectionsSeparately() {
        EntraUser user = user(InvitationStatus.VERIFICATION_SUCCESS, true, SilasAccountStatus.ACTIVE);
        Fixture fixture = fixture(List.of(user.getId()), List.of(user), List.of(), 11);

        fixture.service.refreshBatch(null, ACTOR);

        verify(fixture.session).setJdbcBatchSize(50);
        var order = inOrder(fixture.users, fixture.profiles);
        order.verify(fixture.users).findRefreshUserIds(PageRequest.of(0, 11));
        order.verify(fixture.users).findRefreshUsersForUpdate(List.of(user.getId()));
        order.verify(fixture.profiles).findRefreshProfilesForUpdate(List.of(user.getId()));
        order.verify(fixture.profiles).fetchRefreshProfileRoles(List.of(user.getId()));
        order.verify(fixture.profiles).fetchRefreshProfileOffices(List.of(user.getId()));
    }

    @Test
    void refreshBatch_usesAfterCursorForNextPage() {
        UUID afterId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();
        EntraUser user = user(InvitationStatus.VERIFICATION_SUCCESS, true, SilasAccountStatus.ACTIVE);
        user.setId(nextId);
        Fixture fixture = fixture(List.of(nextId), List.of(user), List.of(), 6);

        SilasStatusRefreshBatchService.BatchResult result = fixture.service.refreshBatch(afterId, ACTOR);

        assertThat(result.lastUserId()).isEqualTo(nextId);
        verify(fixture.users).findRefreshUserIdsAfter(afterId, PageRequest.of(0, 6));
        verify(fixture.users, never()).findRefreshUserIds(any());
    }

    @Test
    void constructor_rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new SilasStatusRefreshBatchService(
                mock(EntraUserCommandRepository.class), mock(UserProfileCommandRepository.class),
                mock(EntityManager.class), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SiLAS status refresh batch size must be greater than zero");
        assertThatThrownBy(() -> new SilasStatusRefreshBatchService(
                mock(EntraUserCommandRepository.class), mock(UserProfileCommandRepository.class),
                mock(EntityManager.class), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshBatch_propagatesRepositoryAndCollectionFailures() {
        Fixture duringIds = fixture(List.of(), List.of(), List.of(), 4);
        RuntimeException repositoryFailure = new RuntimeException("repository unavailable");
        when(duringIds.users.findRefreshUserIds(any())).thenThrow(repositoryFailure);
        assertThatThrownBy(() -> duringIds.service.refreshBatch(null, ACTOR)).isSameAs(repositoryFailure);

        EntraUser user = user(InvitationStatus.VERIFICATION_SUCCESS, true, SilasAccountStatus.ACTIVE);
        Fixture duringCollections = fixture(List.of(user.getId()), List.of(user), List.of(), 4);
        RuntimeException collectionFailure = new RuntimeException("fetch failed");
        when(duringCollections.profiles.fetchRefreshProfileRoles(List.of(user.getId()))).thenThrow(collectionFailure);
        assertThatThrownBy(() -> duringCollections.service.refreshBatch(null, ACTOR)).isSameAs(collectionFailure);
        verify(duringCollections.profiles, never()).fetchRefreshProfileOffices(any());
    }

    private static EntraUser user(InvitationStatus invitationStatus, boolean active, SilasAccountStatus status) {
        return EntraUser.builder()
                .id(UUID.randomUUID())
                .invitationStatus(invitationStatus)
                .active(active)
                .silasAccountStatus(status)
                .build();
    }

    private static UserProfile profile(EntraUser user, UserType type, boolean hasRoles, boolean hasOffices,
                                       boolean unrestricted, UserProfileSilasStatus status) {
        return UserProfile.builder()
                .id(UUID.randomUUID())
                .entraUser(user)
                .userType(type)
                .appRoles(hasRoles ? new HashSet<>(List.of(AppRole.builder().build())) : new HashSet<>())
                .offices(hasOffices ? new HashSet<>(List.of(Office.builder().build())) : new HashSet<>())
                .unrestrictedOfficeAccess(unrestricted)
                .silasStatus(status)
                .build();
    }

    private static Fixture fixture(List<UUID> selectedIds, List<EntraUser> lockedUsers,
                                   List<UserProfile> lockedProfiles, int batchSize) {
        EntraUserCommandRepository users = mock(EntraUserCommandRepository.class);
        UserProfileCommandRepository profiles = mock(UserProfileCommandRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        Session session = mock(Session.class);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(users.findRefreshUserIds(any())).thenReturn(selectedIds);
        when(users.findRefreshUserIdsAfter(any(), any())).thenReturn(selectedIds);
        when(users.findRefreshUsersForUpdate(selectedIds)).thenReturn(lockedUsers);
        when(profiles.findRefreshProfilesForUpdate(selectedIds)).thenReturn(lockedProfiles);
        when(profiles.fetchRefreshProfileRoles(selectedIds)).thenReturn(lockedProfiles);
        when(profiles.fetchRefreshProfileOffices(selectedIds)).thenReturn(lockedProfiles);
        return new Fixture(users, profiles, entityManager, session,
                new SilasStatusRefreshBatchService(users, profiles, entityManager, batchSize));
    }

    private record Fixture(EntraUserCommandRepository users, UserProfileCommandRepository profiles,
                           EntityManager entityManager, Session session, SilasStatusRefreshBatchService service) {
    }
}
