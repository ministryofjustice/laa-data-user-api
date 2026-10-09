package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @Mock
    private EntraUserCommandRepository entraUserCommandRepository;

    private UserCommandService userCommandService;

    private final UUID entraUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        userCommandService = new UserCommandService(entraUserCommandRepository);
    }

    @Nested
    @DisplayName("isExternalUser tests")
    class IsExternalUserTests {

        @Test
        @DisplayName("Should return true when user's first profile type is EXTERNAL")
        void isExternalUser_WhenUserIsExternal_ReturnsTrue() {
            UserProfile profile = UserProfile.builder()
                    .id(UUID.randomUUID())
                    .userType(UserType.EXTERNAL)
                    .build();

            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .userProfiles(Set.of(profile))
                    .build();

            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isExternalUser(entraUserId);

            assertThat(result).isTrue();
            verify(entraUserCommandRepository).findById(entraUserId);
        }

        @Test
        @DisplayName("Should return false when user's first profile type is not EXTERNAL")
        void isExternalUser_WhenUserIsInternal_ReturnsFalse() {
            UserProfile profile = UserProfile.builder()
                    .id(UUID.randomUUID())
                    .userType(UserType.INTERNAL)
                    .build();

            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .userProfiles(Set.of(profile))
                    .build();

            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isExternalUser(entraUserId);

            assertThat(result).isFalse();
            verify(entraUserCommandRepository).findById(entraUserId);
        }

        @Test
        @DisplayName("Should return false when user has no profiles")
        void isExternalUser_WhenUserHasNoProfiles_ReturnsFalse() {
            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .userProfiles(Collections.emptySet())
                    .build();

            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isExternalUser(entraUserId);

            assertThat(result).isFalse();
            verify(entraUserCommandRepository).findById(entraUserId);
        }

        @Test
        @DisplayName("Should throw RuntimeException when user is not found in repository")
        void isExternalUser_WhenUserNotFound_ThrowsRuntimeException() {
            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCommandService.isExternalUser(entraUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Entra user not found for id: " + entraUserId);

            verify(entraUserCommandRepository).findById(entraUserId);
        }
    }

    @Nested
    @DisplayName("isInternalUser tests")
    class IsInternalUserTests {

        @Test
        @DisplayName("Should return true when user's first profile type is INTERNAL")
        void isInternalUser_WhenUserIsInternal_ReturnsTrue() {
            UserProfile profile = UserProfile.builder()
                    .id(UUID.randomUUID())
                    .userType(UserType.INTERNAL)
                    .build();

            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .userProfiles(Set.of(profile))
                    .build();

            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isInternalUser(entraUserId);

            assertThat(result).isTrue();
            verify(entraUserCommandRepository).findById(entraUserId);
        }

        @Test
        @DisplayName("Should return false when user's first profile type is not INTERNAL")
        void isInternalUser_WhenUserIsExternal_ReturnsFalse() {
            UserProfile profile = UserProfile.builder()
                    .id(UUID.randomUUID())
                    .userType(UserType.EXTERNAL)
                    .build();

            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .userProfiles(Set.of(profile))
                    .build();

            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isInternalUser(entraUserId);

            assertThat(result).isFalse();
            verify(entraUserCommandRepository).findById(entraUserId);
        }

        @Test
        @DisplayName("Should return false when user has no profiles")
        void isInternalUser_WhenUserHasNoProfiles_ReturnsFalse() {
            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .userProfiles(Collections.emptySet())
                    .build();

            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isInternalUser(entraUserId);

            assertThat(result).isFalse();
            verify(entraUserCommandRepository).findById(entraUserId);
        }

        @Test
        @DisplayName("Should throw RuntimeException when user is not found in repository")
        void isInternalUser_WhenUserNotFound_ThrowsRuntimeException() {
            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCommandService.isInternalUser(entraUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Entra user not found for id: " + entraUserId);

            verify(entraUserCommandRepository).findById(entraUserId);
        }
    }

    @Nested
    @DisplayName("getActiveUserProfile tests")
    class GetActiveUserProfileTests {

        @Test
        @DisplayName("Should return active profile when one exists among multiple profiles")
        void getActiveUserProfile_WhenActiveProfileExists_ReturnsProfile() {
            UserProfile inactiveProfile = UserProfile.builder()
                    .id(UUID.randomUUID())
                    .activeProfile(false)
                    .userType(UserType.EXTERNAL)
                    .build();

            UserProfile activeProfile = UserProfile.builder()
                    .id(UUID.randomUUID())
                    .activeProfile(true)
                    .userType(UserType.EXTERNAL)
                    .build();

            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .userProfiles(Set.of(inactiveProfile, activeProfile))
                    .build();

            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.of(entraUser));

            UserProfile result = userCommandService.getActiveUserProfile(entraUserId);

            assertThat(result).isNotNull();
            assertThat(result).isSameAs(activeProfile);
            assertThat(result.isActiveProfile()).isTrue();
            verify(entraUserCommandRepository).findById(entraUserId);
        }

        @Test
        @DisplayName("Should throw RuntimeException when user exists but has no active profiles")
        void getActiveUserProfile_WhenNoActiveProfileExists_ThrowsRuntimeException() {
            UserProfile inactiveProfile1 = UserProfile.builder()
                    .id(UUID.randomUUID())
                    .activeProfile(false)
                    .build();

            UserProfile inactiveProfile2 = UserProfile.builder()
                    .id(UUID.randomUUID())
                    .activeProfile(false)
                    .build();

            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .userProfiles(Set.of(inactiveProfile1, inactiveProfile2))
                    .build();

            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.of(entraUser));

            assertThatThrownBy(() -> userCommandService.getActiveUserProfile(entraUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Active profile not found for user id: " + entraUserId);

            verify(entraUserCommandRepository).findById(entraUserId);
        }

        @Test
        @DisplayName("Should throw RuntimeException when user has empty profile list")
        void getActiveUserProfile_WhenProfileListIsEmpty_ThrowsRuntimeException() {
            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .userProfiles(Collections.emptySet())
                    .build();

            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.of(entraUser));

            assertThatThrownBy(() -> userCommandService.getActiveUserProfile(entraUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Active profile not found for user id: " + entraUserId);

            verify(entraUserCommandRepository).findById(entraUserId);
        }

        @Test
        @DisplayName("Should throw RuntimeException when user is not found in repository")
        void getActiveUserProfile_WhenUserNotFound_ThrowsRuntimeException() {
            when(entraUserCommandRepository.findById(entraUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCommandService.getActiveUserProfile(entraUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Entra user not found for id: " + entraUserId);

            verify(entraUserCommandRepository).findById(entraUserId);
        }
    }
}
