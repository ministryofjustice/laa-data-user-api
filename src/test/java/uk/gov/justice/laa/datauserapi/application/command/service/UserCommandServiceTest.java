package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

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

    @Mock
    private UserProfileCommandRepository userProfileCommandRepository;

    private UserCommandService userCommandService;

    private final UUID entraUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        userCommandService = new UserCommandService(entraUserCommandRepository, userProfileCommandRepository);
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
                    .entraOid(String.valueOf(entraUserId))
                    .userProfiles(Set.of(profile))
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isExternalUser(entraUserId);

            assertThat(result).isTrue();
            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
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
                    .entraOid(String.valueOf(entraUserId))
                    .userProfiles(Set.of(profile))
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isExternalUser(entraUserId);

            assertThat(result).isFalse();
            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
        }

        @Test
        @DisplayName("Should return false when user has no profiles")
        void isExternalUser_WhenUserHasNoProfiles_ReturnsFalse() {
            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .entraOid(String.valueOf(entraUserId))
                    .userProfiles(Collections.emptySet())
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isExternalUser(entraUserId);

            assertThat(result).isFalse();
            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
        }

        @Test
        @DisplayName("Should throw RuntimeException when user is not found in repository")
        void isExternalUser_WhenUserNotFound_ThrowsRuntimeException() {
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCommandService.isExternalUser(entraUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Entra user not found for oid: " + entraUserId);

            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
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
                    .entraOid(String.valueOf(entraUserId))
                    .userProfiles(Set.of(profile))
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isInternalUser(entraUserId);

            assertThat(result).isTrue();
            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
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
                    .entraOid(String.valueOf(entraUserId))
                    .userProfiles(Set.of(profile))
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isInternalUser(entraUserId);

            assertThat(result).isFalse();
            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
        }

        @Test
        @DisplayName("Should return false when user has no profiles")
        void isInternalUser_WhenUserHasNoProfiles_ReturnsFalse() {
            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .entraOid(String.valueOf(entraUserId))
                    .userProfiles(Collections.emptySet())
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            boolean result = userCommandService.isInternalUser(entraUserId);

            assertThat(result).isFalse();
            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
        }

        @Test
        @DisplayName("Should throw RuntimeException when user is not found in repository")
        void isInternalUser_WhenUserNotFound_ThrowsRuntimeException() {
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCommandService.isInternalUser(entraUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Entra user not found for oid: " + entraUserId);

            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
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
                    .entraOid(String.valueOf(entraUserId))
                    .userProfiles(Set.of(inactiveProfile, activeProfile))
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            UserProfile result = userCommandService.getActiveUserProfile(entraUserId);

            assertThat(result).isNotNull();
            assertThat(result).isSameAs(activeProfile);
            assertThat(result.isActiveProfile()).isTrue();
            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
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
                    .entraOid(String.valueOf(entraUserId))
                    .userProfiles(Set.of(inactiveProfile1, inactiveProfile2))
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            assertThatThrownBy(() -> userCommandService.getActiveUserProfile(entraUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Active profile not found for user oid: " + entraUserId);

            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
        }

        @Test
        @DisplayName("Should throw RuntimeException when user has empty profile list")
        void getActiveUserProfile_WhenProfileListIsEmpty_ThrowsRuntimeException() {
            EntraUser entraUser = EntraUser.builder()
                    .id(entraUserId)
                    .entraOid(String.valueOf(entraUserId))
                    .userProfiles(Collections.emptySet())
                    .build();

            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            assertThatThrownBy(() -> userCommandService.getActiveUserProfile(entraUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Active profile not found for user oid: " + entraUserId);

            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
        }

        @Test
        @DisplayName("Should throw RuntimeException when user is not found in repository")
        void getActiveUserProfile_WhenUserNotFound_ThrowsRuntimeException() {
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCommandService.getActiveUserProfile(entraUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User account not found for oid: " + entraUserId);

            verify(entraUserCommandRepository).findByEntraOid(String.valueOf(entraUserId));
        }
    }

    @Nested
    @DisplayName("policy lookup tests")
    class PolicyLookupTests {

        @Test
        void isMultiFirmUser_ReturnsFlagFromUser() {
            EntraUser entraUser = EntraUser.builder().entraOid(String.valueOf(entraUserId)).multiFirmUser(true).build();
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            assertThat(userCommandService.isMultiFirmUser(entraUserId)).isTrue();
        }

        @Test
        void findActiveFirmId_ReturnsFirmOfActiveProfile() {
            UUID firmId = UUID.randomUUID();
            UserProfile inactive = UserProfile.builder().id(UUID.randomUUID()).activeProfile(false)
                    .firm(Firm.builder().id(UUID.randomUUID()).build()).build();
            UserProfile active = UserProfile.builder().id(UUID.randomUUID()).activeProfile(true)
                    .firm(Firm.builder().id(firmId).build()).build();
            EntraUser entraUser = EntraUser.builder().userProfiles(Set.of(inactive, active)).build();
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            assertThat(userCommandService.findActiveFirmId(entraUserId)).contains(firmId);
        }

        @Test
        void findActiveFirmId_EmptyWhenNoActiveProfileOrFirm() {
            UserProfile activeNoFirm = UserProfile.builder().id(UUID.randomUUID()).activeProfile(true).build();
            EntraUser entraUser = EntraUser.builder().userProfiles(Set.of(activeNoFirm)).build();
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));

            assertThat(userCommandService.findActiveFirmId(entraUserId)).isEmpty();
        }

        @Test
        void isDeactivated_ReflectsAccountStatus() {
            EntraUser entraUser = EntraUser.builder().userAccountStatus(UserAccountStatus.DEACTIVATED).build();
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));
            assertThat(userCommandService.isDeactivated(entraUserId)).isTrue();

            entraUser.setUserAccountStatus(UserAccountStatus.ACTIVE);
            assertThat(userCommandService.isDeactivated(entraUserId)).isFalse();
        }

        @Test
        void findDeactivationType_ReturnsStoredTypeOrEmpty() {
            EntraUser entraUser = EntraUser.builder().deactivationType(DeactivationType.FIRM).build();
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.of(entraUser));
            assertThat(userCommandService.findDeactivationType(entraUserId)).contains(DeactivationType.FIRM);

            entraUser.setDeactivationType(null);
            assertThat(userCommandService.findDeactivationType(entraUserId)).isEmpty();
        }

        @Test
        void throwsNotFoundWhenUserMissing() {
            when(entraUserCommandRepository.findByEntraOid(String.valueOf(entraUserId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCommandService.isMultiFirmUser(entraUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> userCommandService.findActiveFirmId(entraUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> userCommandService.isDeactivated(entraUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> userCommandService.findDeactivationType(entraUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findProfileFirmId tests")
    class FindProfileFirmIdTests {

        @Test
        void returnsFirmIdOfProfile() {
            UUID profileId = UUID.randomUUID();
            UUID firmId = UUID.randomUUID();
            UserProfile profile = UserProfile.builder().id(profileId).firm(Firm.builder().id(firmId).build()).build();
            when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.of(profile));

            assertThat(userCommandService.findProfileFirmId(profileId)).contains(firmId);
        }

        @Test
        void returnsEmptyWhenProfileHasNoFirm() {
            UUID profileId = UUID.randomUUID();
            when(userProfileCommandRepository.findById(profileId))
                    .thenReturn(Optional.of(UserProfile.builder().id(profileId).build()));

            assertThat(userCommandService.findProfileFirmId(profileId)).isEmpty();
        }

        @Test
        void throwsNotFoundWhenProfileMissing() {
            UUID profileId = UUID.randomUUID();
            when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCommandService.findProfileFirmId(profileId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User profile not found with ID: " + profileId);
        }
    }

    @Nested
    @DisplayName("profile target lookups")
    class ProfileTargetLookupTests {

        @Test
        void returnsUserTypeAndEntraOidOfProfile() {
            UUID profileId = UUID.randomUUID();
            UserProfile profile = UserProfile.builder().id(profileId).userType(UserType.EXTERNAL)
                    .entraUser(EntraUser.builder().entraOid("oid-1").build()).build();
            when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.of(profile));

            assertThat(userCommandService.findProfileUserType(profileId)).contains(UserType.EXTERNAL);
            assertThat(userCommandService.findProfileEntraOid(profileId)).contains("oid-1");
        }

        @Test
        void throwsNotFoundWhenProfileMissing() {
            UUID profileId = UUID.randomUUID();
            when(userProfileCommandRepository.findById(profileId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCommandService.findProfileUserType(profileId))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> userCommandService.findProfileEntraOid(profileId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
