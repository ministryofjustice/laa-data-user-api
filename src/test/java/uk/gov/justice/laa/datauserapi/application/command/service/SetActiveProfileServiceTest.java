package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.SetActiveProfileNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.SetActiveProfileUserNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetActiveProfileServiceTest {

    @Mock
    private EntraUserCommandRepository entraUserCommandRepository;
    @Mock
    private UserProfileCommandRepository userProfileCommandRepository;

    private SetActiveProfileService service;

    @BeforeEach
    void setUp() {
        service = new SetActiveProfileService(entraUserCommandRepository, userProfileCommandRepository);
    }

    @Test
    void switchesActiveProfileAndDeactivatesEveryOtherProfileForThatUser() {
        UUID userId = UUID.randomUUID();
        UUID userEntraObjectId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID selectedId = UUID.randomUUID();
        EntraUser user = EntraUser.builder().id(userId).entraOid(userEntraObjectId.toString()).build();
        UserProfile current = profile(firstId, true);
        UserProfile selected = profile(selectedId, false);
        UserProfile unrelatedUserProfile = profile(UUID.randomUUID(), true);
        when(entraUserCommandRepository.findByEntraOidForUpdate(userEntraObjectId.toString()))
                .thenReturn(Optional.of(user));
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of(current, selected));

        CommandResult result = service.setActiveProfile(
                new SetActiveProfileCommand(userEntraObjectId, selectedId), actor());

        assertThat(result.success()).isTrue();
        assertThat(current.isActiveProfile()).isFalse();
        assertThat(selected.isActiveProfile()).isTrue();
        assertThat(unrelatedUserProfile.isActiveProfile()).isTrue();
        verify(userProfileCommandRepository).saveAll(List.of(current, selected));
    }

    @Test
    void repeatingTheAlreadyActiveProfileDoesNotWrite() {
        UUID userEntraObjectId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        EntraUser user = EntraUser.builder().entraOid(userEntraObjectId.toString()).build();
        UserProfile selected = profile(profileId, true);
        UserProfile inactive = profile(UUID.randomUUID(), false);
        when(entraUserCommandRepository.findByEntraOidForUpdate(userEntraObjectId.toString()))
                .thenReturn(Optional.of(user));
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of(selected, inactive));

        CommandResult result = service.setActiveProfile(new SetActiveProfileCommand(userEntraObjectId, profileId), actor());

        assertThat(result.success()).isTrue();
        assertThat(selected.isActiveProfile()).isTrue();
        assertThat(inactive.isActiveProfile()).isFalse();
        verify(userProfileCommandRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void unknownUserReturnsNotFoundWithoutLoadingOrChangingProfiles() {
        UUID userEntraObjectId = UUID.randomUUID();
        when(entraUserCommandRepository.findByEntraOidForUpdate(userEntraObjectId.toString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActiveProfile(
                new SetActiveProfileCommand(userEntraObjectId, UUID.randomUUID()), actor()))
                .isInstanceOf(SetActiveProfileUserNotFoundException.class);

        verify(userProfileCommandRepository, never()).findAllByEntraUser(org.mockito.ArgumentMatchers.any());
        verify(userProfileCommandRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void profileNotOwnedByRequestedUserReturnsNotFoundWithoutChangingState() {
        UUID userEntraObjectId = UUID.randomUUID();
        UUID requestedProfileId = UUID.randomUUID();
        EntraUser user = EntraUser.builder().entraOid(userEntraObjectId.toString()).build();
        UserProfile existingProfile = profile(UUID.randomUUID(), true);
        when(entraUserCommandRepository.findByEntraOidForUpdate(userEntraObjectId.toString()))
                .thenReturn(Optional.of(user));
        when(userProfileCommandRepository.findAllByEntraUser(user)).thenReturn(List.of(existingProfile));

        assertThatThrownBy(() -> service.setActiveProfile(
                new SetActiveProfileCommand(userEntraObjectId, requestedProfileId), actor()))
                .isInstanceOf(SetActiveProfileNotFoundException.class);

        assertThat(existingProfile.isActiveProfile()).isTrue();
        verify(userProfileCommandRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyCollection());
    }

    private static UserProfile profile(UUID id, boolean active) {
        return UserProfile.builder().id(id).activeProfile(active).build();
    }

    private static AuthenticatedEntraUser actor() {
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).build();
    }
}
