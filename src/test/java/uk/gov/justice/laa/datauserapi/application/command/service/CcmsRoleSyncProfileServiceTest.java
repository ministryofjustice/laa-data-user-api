package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

class CcmsRoleSyncProfileServiceTest {
    private final UserProfileCommandRepository repository = mock(UserProfileCommandRepository.class);
    private final RoleChangeNotificationService notifications = mock(RoleChangeNotificationService.class);
    private final CcmsRoleSyncProfileService service = new CcmsRoleSyncProfileService(repository, notifications);
    private final UUID id = UUID.randomUUID();

    @Test
    void groupsOnlyLegacyRolesAndMarksSuccessfulProfile() {
        var profile = profile(role("a", "one", true), role("a", "two", true), role("a", "local", false));

        assertThat(service.syncProfile(id, "actor"))
                .isEqualTo(new CcmsRoleSyncProfileService.ProfileSyncResult(true, null));
        assertThat(profile.isLastCcmsSyncSuccessful()).isTrue();
        assertThat(profile.getLastModifiedBy()).isEqualTo("actor");
        assertThat(profile.getLastModified()).isNotNull();
        verify(notifications).sendSyncMessage(profile, "a", Set.of("one", "two"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void retriesFailuresAndAttemptsOtherApplicationsBeforeUpdatingFlag() {
        var profile = profile(role("a", "one", true), role("b", "two", true));
        var failure = new CcmsSyncException(HttpStatus.GATEWAY_TIMEOUT, "Timeout");
        doThrow(failure).when(notifications).sendSyncMessage(profile, "a", Set.of("one"));

        assertThat(service.syncProfile(id, "actor").failure()).isSameAs(failure);
        assertThat(profile.isLastCcmsSyncSuccessful()).isFalse();
        verify(notifications, times(3)).sendSyncMessage(profile, "a", Set.of("one"));
        verify(notifications).sendSyncMessage(profile, "b", Set.of("two"));
    }

    @Test
    void transientFailureCanSucceedOnRetry() {
        var profile = profile(role("a", "one", true));
        doThrow(new CcmsSyncException(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable")).doNothing()
                .when(notifications).sendSyncMessage(profile, "a", Set.of("one"));

        assertThat(service.syncProfile(id, "actor").failure()).isNull();
        assertThat(profile.isLastCcmsSyncSuccessful()).isTrue();
        verify(notifications, times(2)).sendSyncMessage(profile, "a", Set.of("one"));
    }

    @Test
    void doesNotSkipSelectedProfileIfAnotherTriggerAlreadySyncedIt() {
        var profile = profile(role("a", "one", true));
        profile.setLastCcmsSyncSuccessful(true);
        assertThat(service.syncProfile(id, "actor").processed()).isTrue();
        verify(notifications).sendSyncMessage(profile, "a", Set.of("one"));
    }

    @Test
    void deletedProfileIsNotCountedAsProcessed() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThat(service.syncProfile(id, "actor"))
                .isEqualTo(new CcmsRoleSyncProfileService.ProfileSyncResult(false, null));
        verifyNoInteractions(notifications);
    }

    private UserProfile profile(AppRole... roles) {
        var profile = UserProfile.builder().id(id).legacyUserId(UUID.randomUUID()).appRoles(Set.of(roles)).build();
        when(repository.findById(id)).thenReturn(Optional.of(profile));
        return profile;
    }

    private AppRole role(String app, String code, boolean legacy) {
        return AppRole.builder().app(App.builder().entraOid(app).build())
                .roleIdentifier(code).legacySync(legacy).build();
    }
}
