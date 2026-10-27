package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncProfileService.ProfileSyncResult;
import uk.gov.justice.laa.datauserapi.contracts.response.TriggerCcmsRoleSyncResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class CcmsRoleSyncServiceTest {
    private final UserProfileCommandRepository repository = mock(UserProfileCommandRepository.class);
    private final CcmsRoleSyncProfileService profileService = mock(CcmsRoleSyncProfileService.class);
    private final CcmsRoleSyncService service = new CcmsRoleSyncService(repository, profileService);

    @Test
    void attemptsEveryProfileBeforeReportingPartialFailureAndAllowsRetrigger() {
        var first = profile();
        var second = profile();
        var third = profile();
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of(first, second, third));
        when(profileService.syncProfile(first.getId(), "actor"))
                .thenReturn(new ProfileSyncResult(true, new CcmsSyncException(HttpStatus.BAD_GATEWAY, "Unexpected response")));
        when(profileService.syncProfile(second.getId(), "actor"))
                .thenReturn(new ProfileSyncResult(true, new CcmsSyncException(HttpStatus.GATEWAY_TIMEOUT, "Timed out")));
        when(profileService.syncProfile(third.getId(), "actor")).thenReturn(new ProfileSyncResult(true, null));

        assertThatThrownBy(() -> service.sync("actor"))
                .isInstanceOfSatisfying(CcmsSyncException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(ex.getMessage()).contains("1 profiles succeeded", "2 profiles failed");
                });
        verify(profileService).syncProfile(third.getId(), "actor");
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of());
        assertThat(service.sync("retry")).isEqualTo(new TriggerCcmsRoleSyncResult(true, 0));
    }

    @Test
    void returnsSuccessOnlyAfterAllProfilesComplete() {
        var profile = profile();
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of(profile));
        when(profileService.syncProfile(profile.getId(), "actor")).thenReturn(new ProfileSyncResult(true, null));
        assertThat(service.sync("actor")).isEqualTo(new TriggerCcmsRoleSyncResult(true, 1));
        verify(profileService).syncProfile(profile.getId(), "actor");
    }

    @Test
    void allowsOverlappingSync() {
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of()).thenAnswer(invocation -> {
            assertThat(service.sync("overlap")).isEqualTo(new TriggerCcmsRoleSyncResult(true, 0));
            return List.of();
        }).thenReturn(List.of());
        assertThat(service.sync("actor").success()).isTrue();
        assertThat(service.sync("actor").success()).isTrue();
    }

    @Test
    void countsOnlyProfilesActuallyProcessed() {
        var first = profile();
        var deleted = profile();
        var third = profile();
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of(first, deleted, third));
        when(profileService.syncProfile(first.getId(), "actor")).thenReturn(new ProfileSyncResult(true, null));
        when(profileService.syncProfile(deleted.getId(), "actor")).thenReturn(new ProfileSyncResult(false, null));
        when(profileService.syncProfile(third.getId(), "actor")).thenReturn(new ProfileSyncResult(true, null));

        assertThat(service.sync("actor")).isEqualTo(new TriggerCcmsRoleSyncResult(true, 2));
    }

    @Test
    void repeatedTriggerProcessesSelectedProfilesAgain() {
        var profile = profile();
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of(profile));
        when(profileService.syncProfile(profile.getId(), "actor")).thenReturn(new ProfileSyncResult(true, null));
        assertThat(service.sync("actor").userProfileCount()).isEqualTo(1);
        assertThat(service.sync("actor").userProfileCount()).isEqualTo(1);
        verify(profileService, times(2)).syncProfile(profile.getId(), "actor");
    }

    private UserProfile profile() {
        return UserProfile.builder().id(UUID.randomUUID()).build();
    }
}
