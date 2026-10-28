package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncProfileService.ProfileSyncResult;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CcmsRoleSyncServiceTest {
    private final UserProfileCommandRepository repository = mock(UserProfileCommandRepository.class);
    private final CcmsRoleSyncProfileService profiles = mock(CcmsRoleSyncProfileService.class);
    private final CcmsRoleSyncService service = new CcmsRoleSyncService(repository, profiles);

    @Test
    void continuesAfterDownstreamFailureAndDeletedProfile() {
        var first = profile();
        var second = profile();
        var third = profile();
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of(first, second, third));
        when(profiles.syncProfile(first.getId(), "actor")).thenReturn(new ProfileSyncResult(true,
                new CcmsSyncException(HttpStatus.GATEWAY_TIMEOUT, "Timed out")));
        when(profiles.syncProfile(second.getId(), "actor")).thenReturn(new ProfileSyncResult(false, null));
        when(profiles.syncProfile(third.getId(), "actor")).thenReturn(new ProfileSyncResult(true, null));

        service.sync("actor");

        verify(profiles).syncProfile(first.getId(), "actor");
        verify(profiles).syncProfile(second.getId(), "actor");
        verify(profiles).syncProfile(third.getId(), "actor");
    }

    @Test
    void noEligibleProfilesDoesNotSendNotifications() {
        when(repository.findUserProfilesForCcmsSync()).thenReturn(List.of());
        service.sync("actor");
        verifyNoInteractions(profiles);
    }

    private UserProfile profile() {
        return UserProfile.builder().id(UUID.randomUUID()).build();
    }
}
