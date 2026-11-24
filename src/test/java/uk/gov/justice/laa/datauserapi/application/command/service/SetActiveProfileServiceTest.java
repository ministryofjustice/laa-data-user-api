package uk.gov.justice.laa.datauserapi.application.command.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.justice.laa.datauserapi.application.command.policy.SetActiveProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetActiveProfileServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID USER_OID = UUID.randomUUID();
    private static final UUID FIRST_ID = UUID.randomUUID();
    private static final UUID SELECTED_ID = UUID.randomUUID();

    @Mock private EntraUserCommandRepository users;
    @Mock private UserProfileCommandRepository profiles;
    @Mock private EntityManager entityManager;
    private SetActiveProfileService service;

    @BeforeEach
    void setUp() {
        service = new SetActiveProfileService(users, profiles, entityManager, new SetActiveProfilePolicy());
    }

    @Test
    void switchesAndRefreshesBothProfilesAfterLocking() {
        EntraUser user = user();
        UserProfile current = profile(user, FIRST_ID, true, UserType.EXTERNAL);
        UserProfile selected = profile(user, SELECTED_ID, false, UserType.EXTERNAL);
        stubUser(user, List.of(current, selected));

        assertThat(service.setActiveProfile(command(), actor(USER_ID)).success()).isTrue();
        assertThat(current.isActiveProfile()).isFalse();
        assertThat(selected.isActiveProfile()).isTrue();
        verify(entityManager).refresh(current, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(selected, LockModeType.PESSIMISTIC_WRITE);
        verify(profiles).saveAll(List.of(current, selected));
    }

    @Test
    void alreadyActiveProfileIsIdempotent() {
        EntraUser user = user();
        UserProfile selected = profile(user, SELECTED_ID, true, UserType.EXTERNAL);
        UserProfile inactive = profile(user, FIRST_ID, false, UserType.EXTERNAL);
        stubUser(user, List.of(selected, inactive));

        assertThat(service.setActiveProfile(command(), actor(USER_ID)).success()).isTrue();
        assertThat(selected.isActiveProfile()).isTrue();
        assertThat(inactive.isActiveProfile()).isFalse();
        verify(profiles, never()).saveAll(anyCollection());
    }

    @Test
    void missingUserThrowsGenericUserNotFound() {
        when(users.findByEntraOidForUpdate(USER_OID.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActiveProfile(command(), actor(USER_ID)))
                .isInstanceOf(UserNotFoundException.class);
        verify(profiles, never()).findRefreshProfilesForUpdate(anyList());
    }

    @Test
    void missingProfileDoesNotChangeOtherProfiles() {
        EntraUser user = user();
        UserProfile current = profile(user, FIRST_ID, true, UserType.EXTERNAL);
        stubUser(user, List.of(current));

        assertThatThrownBy(() -> service.setActiveProfile(command(), actor(USER_ID)))
                .isInstanceOf(UserProfileNotFoundException.class);
        assertThat(current.isActiveProfile()).isTrue();
        verify(profiles, never()).saveAll(anyCollection());
    }

    @Test
    void ownershipIsRecheckedBeforeMutation() {
        EntraUser user = user();
        UserProfile current = profile(user, FIRST_ID, true, UserType.EXTERNAL);
        UserProfile selected = profile(user, SELECTED_ID, false, UserType.EXTERNAL);
        stubUser(user, List.of(current, selected));

        assertThatThrownBy(() -> service.setActiveProfile(command(), actor(UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(current.isActiveProfile()).isTrue();
        assertThat(selected.isActiveProfile()).isFalse();
        verify(profiles, never()).saveAll(anyCollection());
    }

    @Test
    void currentActorTypeIsRecheckedBeforeMutation() {
        EntraUser user = user();
        UserProfile current = profile(user, FIRST_ID, true, UserType.INTERNAL);
        UserProfile selected = profile(user, SELECTED_ID, false, UserType.EXTERNAL);
        stubUser(user, List.of(current, selected));

        assertThatThrownBy(() -> service.setActiveProfile(command(), actor(USER_ID)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(current.isActiveProfile()).isTrue();
        assertThat(selected.isActiveProfile()).isFalse();
        verify(profiles, never()).saveAll(anyCollection());
    }

    private void stubUser(EntraUser user, List<UserProfile> userProfiles) {
        when(users.findByEntraOidForUpdate(USER_OID.toString())).thenReturn(Optional.of(user));
        when(profiles.findRefreshProfilesForUpdate(List.of(USER_ID))).thenReturn(userProfiles);
    }

    private static EntraUser user() {
        return EntraUser.builder().id(USER_ID).entraOid(USER_OID.toString()).build();
    }

    private static UserProfile profile(EntraUser user, UUID id, boolean active, UserType type) {
        return UserProfile.builder().id(id).entraUser(user).activeProfile(active).userType(type).build();
    }

    private static SetActiveProfileCommand command() {
        return new SetActiveProfileCommand(USER_OID, SELECTED_ID);
    }

    private static AuthenticatedEntraUser actor(UUID id) {
        AuthenticatedUserProfile current = AuthenticatedUserProfile.builder().id(FIRST_ID)
                .activeProfile(true).userType(UserType.EXTERNAL).build();
        AuthenticatedUserProfile selected = AuthenticatedUserProfile.builder().id(SELECTED_ID)
                .userType(UserType.EXTERNAL).build();
        return AuthenticatedEntraUser.builder().id(id).entraOid(USER_OID.toString())
                .activeProfile(current).userProfiles(Set.of(current, selected)).build();
    }
}
