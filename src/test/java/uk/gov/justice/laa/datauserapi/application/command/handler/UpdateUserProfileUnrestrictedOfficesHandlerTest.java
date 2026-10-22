package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserProfileUnrestrictedOfficesHandlerTest {

    @Mock
    private UserProfileCommandRepository userProfileCommandRepository;

    private UpdateUserProfileUnrestrictedOfficesHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateUserProfileUnrestrictedOfficesHandler(userProfileCommandRepository);
    }

    @Test
    void handle_enablesUnrestrictedAccessAndClearsAssignedOffices() {
        UUID profileId = UUID.randomUUID();
        String actorOid = UUID.randomUUID().toString();
        UserProfile profile = profile(profileId);
        when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.of(profile));

        CommandResult result = handler.handle(
                new UpdateUserProfileUnrestrictedOfficesCommand(profileId, true), actorOid);

        assertThat(result.success()).isTrue();
        assertThat(profile.isUnrestrictedOfficeAccess()).isTrue();
        assertThat(profile.getOffices()).isEmpty();
        assertThat(profile.getLastModified()).isNotNull();
        assertThat(profile.getLastModifiedBy()).isEqualTo(actorOid);
        verify(userProfileCommandRepository).save(profile);
    }

    @Test
    void handle_disablesUnrestrictedAccessAndClearsAssignedOffices() {
        UUID profileId = UUID.randomUUID();
        UserProfile profile = profile(profileId);
        profile.setUnrestrictedOfficeAccess(true);
        when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.of(profile));

        CommandResult result = handler.handle(
                new UpdateUserProfileUnrestrictedOfficesCommand(profileId, false), "actor");

        assertThat(result.success()).isTrue();
        assertThat(profile.isUnrestrictedOfficeAccess()).isFalse();
        assertThat(profile.getOffices()).isEmpty();
        verify(userProfileCommandRepository).save(profile);
    }

    @Test
    void handle_throwsNotFoundWhenProfileDoesNotExist() {
        UUID profileId = UUID.randomUUID();
        when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new UpdateUserProfileUnrestrictedOfficesCommand(profileId, true), "actor"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(profileId.toString());

        verify(userProfileCommandRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static UserProfile profile(UUID id) {
        return UserProfile.builder()
                .id(id)
                .offices(new HashSet<>(Set.of(Office.builder().id(UUID.randomUUID()).build())))
                .build();
    }
}
