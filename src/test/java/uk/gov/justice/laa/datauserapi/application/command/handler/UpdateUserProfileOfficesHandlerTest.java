package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.OfficeCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserProfileOfficesHandlerTest {

    @Mock
    private UserProfileCommandRepository userProfileCommandRepository;

    @Mock
    private OfficeCommandRepository officeCommandRepository;

    private UpdateUserProfileOfficesHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateUserProfileOfficesHandler(userProfileCommandRepository, officeCommandRepository);
    }

    @Test
    void handle_replacesOfficesAndDisablesUnrestrictedAccess() {
        UUID profileId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        Office office = office(firmId);
        UserProfile profile = profile(profileId, firmId, Set.of());
        profile.setUnrestrictedOfficeAccess(true);
        when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(officeCommandRepository.findAllById(Set.of(office.getId()))).thenReturn(List.of(office));
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("actorId").build();

        CommandResult result = handler.handle(
                new UpdateUserProfileOfficesCommand(profileId, Set.of(office.getId())), actor);

        assertThat(result.success()).isTrue();
        assertThat(profile.getOffices()).containsExactly(office);
        assertThat(profile.isUnrestrictedOfficeAccess()).isFalse();
        assertThat(profile.getLastModified()).isNotNull();
        assertThat(profile.getLastModifiedBy()).isEqualTo(actor.id().toString());
        verify(userProfileCommandRepository).save(profile);
    }

    @Test
    void handle_clearsOfficesWhenRequestIsEmpty() {
        UUID profileId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        Office existingOffice = office(firmId);
        UserProfile profile = profile(profileId, firmId, Set.of(existingOffice));
        when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(officeCommandRepository.findAllById(Set.of())).thenReturn(List.of());

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("actorId").build();

        CommandResult result = handler.handle(
                new UpdateUserProfileOfficesCommand(profileId, Set.of()), actor);

        assertThat(result.success()).isTrue();
        assertThat(profile.getOffices()).isEmpty();
        verify(userProfileCommandRepository).save(profile);
    }

    @Test
    void handle_throwsNotFoundWhenProfileDoesNotExist() {
        UUID profileId = UUID.randomUUID();
        when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.empty());

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("actorId").build();

        assertThatThrownBy(() -> handler.handle(
                new UpdateUserProfileOfficesCommand(profileId, Set.of()), actor))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(profileId.toString());

        verifyNoInteractions(officeCommandRepository);
        verify(userProfileCommandRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handle_throwsNotFoundWhenAnOfficeDoesNotExist() {
        UUID profileId = UUID.randomUUID();
        UUID missingOfficeId = UUID.randomUUID();
        UserProfile profile = profile(profileId, UUID.randomUUID(), Set.of());
        when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(officeCommandRepository.findAllById(Set.of(missingOfficeId))).thenReturn(List.of());

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("actorId").build();
        assertThatThrownBy(() -> handler.handle(
                new UpdateUserProfileOfficesCommand(profileId, Set.of(missingOfficeId)), actor))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("offices were not found");

        assertThat(profile.getOffices()).isEmpty();
        verify(userProfileCommandRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handle_returnsFailureWhenOfficeBelongsToAnotherFirm() {
        UUID profileId = UUID.randomUUID();
        UserProfile profile = profile(profileId, UUID.randomUUID(), Set.of());
        Office office = office(UUID.randomUUID());
        when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(officeCommandRepository.findAllById(Set.of(office.getId()))).thenReturn(List.of(office));

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("actorId").build();

        CommandResult result = handler.handle(
                new UpdateUserProfileOfficesCommand(profileId, Set.of(office.getId())), actor);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("must belong to the user profile's firm");
        assertThat(profile.getOffices()).isEmpty();
        verify(userProfileCommandRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handle_returnsFailureWhenProfileHasNoFirmAndOfficesAreRequested() {
        UUID profileId = UUID.randomUUID();
        UserProfile profile = profile(profileId, null, Set.of());
        Office office = office(UUID.randomUUID());
        when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(officeCommandRepository.findAllById(Set.of(office.getId()))).thenReturn(List.of(office));

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("actorId").build();
        CommandResult result = handler.handle(
                new UpdateUserProfileOfficesCommand(profileId, Set.of(office.getId())), actor);

        assertThat(result.success()).isFalse();
        verify(userProfileCommandRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static UserProfile profile(UUID id, UUID firmId, Set<Office> offices) {
        Firm firm = firmId == null ? null : Firm.builder().id(firmId).build();
        return UserProfile.builder()
                .id(id)
                .firm(firm)
                .offices(new HashSet<>(offices))
                .build();
    }

    private static Office office(UUID firmId) {
        return Office.builder()
                .id(UUID.randomUUID())
                .firm(Firm.builder().id(firmId).build())
                .build();
    }
}
