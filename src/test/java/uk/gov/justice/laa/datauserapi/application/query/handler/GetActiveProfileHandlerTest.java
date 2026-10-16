package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.service.QueryAuthorisationService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.model.AppType;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveProfileHandlerTest {

    @Mock
    private UserProfileQueryRepository userProfileQueryRepository;

    @Mock
    private QueryAuthorisationService authorizationService;

    private String actorOid;
    private String userEntraObjectId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        actorOid = UUID.randomUUID().toString();
        userEntraObjectId = UUID.randomUUID().toString();
    }

    @Test
    void returnsActiveProfileWhenAccessible() {
        AuthenticatedUserProfile actorProfile = authenticatedUserProfile(
                UserType.INTERNAL,
                null,
                Set.of(Permission.VIEW_INTERNAL_USER));

        UserProfile targetProfile = buildFullTargetProfile(UserType.INTERNAL);
        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId))
                .thenReturn(Optional.of(targetProfile));
        when(authorizationService.canViewProfile(actorProfile, targetProfile.getUserType(), null)).thenReturn(true);

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, authorizationService);
        UserProfileDetailView result = handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile));

        assertThat(result).isNotNull();
        assertThat(result.userType()).isEqualTo(UserType.INTERNAL);
        assertThat(result.roles()).isNotEmpty();
        assertThat(result.roles().get(0).appEntraClientId()).isEqualTo("test-entra-client-id-123");
    }

    @Test
    void throwsNotFoundWhenProfileDoesNotExist() {
        AuthenticatedUserProfile actorProfile = authenticatedUserProfile(
                UserType.INTERNAL,
                null,
                Set.of(Permission.VIEW_INTERNAL_USER));

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId))
                .thenReturn(Optional.empty());

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, authorizationService);

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active profile not found");
    }

    @Test
    void throwsNotFoundWhenExternalUserNotInActorFirm() {
        Firm actorFirm = Firm.builder()
                .id(UUID.randomUUID())
                .type(FirmType.LEGAL_SERVICES_PROVIDER)
                .name("Actor Firm " + UUID.randomUUID())
                .build();
        AuthenticatedUserProfile actorProfile = authenticatedUserProfile(
                UserType.EXTERNAL,
                actorFirm.getId(),
                Set.of());

        Firm targetFirm = Firm.builder()
                .id(UUID.randomUUID())
                .type(FirmType.LEGAL_SERVICES_PROVIDER)
                .name("Target Firm " + UUID.randomUUID())
                .build();
        UserProfile targetProfile = UserProfile.builder()
                .userType(UserType.EXTERNAL)
                .firm(targetFirm)
                .build();
        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId))
                .thenReturn(Optional.of(targetProfile));
        when(authorizationService.canViewProfile(
                actorProfile,
                targetProfile.getUserType(),
                targetFirm.getId().toString())).thenReturn(false);

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, authorizationService);

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active profile not found");
    }

    @Test
    void internalActorWithViewExternalUserCanViewExternalProfile() {
        AuthenticatedUserProfile actorProfile = authenticatedUserProfile(
                UserType.INTERNAL,
                null,
                Set.of(Permission.VIEW_EXTERNAL_USER));

        UserProfile targetProfile = buildFullTargetProfile(UserType.EXTERNAL);
        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId))
                .thenReturn(Optional.of(targetProfile));
        when(authorizationService.canViewProfile(actorProfile, targetProfile.getUserType(), null)).thenReturn(true);

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, authorizationService);
        UserProfileDetailView result = handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile));

        assertThat(result).isNotNull();
        assertThat(result.userType()).isEqualTo(UserType.EXTERNAL);
        assertThat(result.roles()).isNotEmpty();
        assertThat(result.roles().get(0).appEntraClientId()).isEqualTo("test-entra-client-id-123");
    }

    @Test
    void throwsExceptionWhenActorHasNoActiveProfile() {
        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, authorizationService);

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser(null)))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessageContaining("Actor has no active profile");
    }

    private AuthenticatedUserProfile authenticatedUserProfile(UserType userType, UUID firmId, Set<Permission> permissions) {
        return new AuthenticatedUserProfile(
                UUID.randomUUID(),
                true,
                userType,
                null,
                firmId,
                null,
                null,
                UserProfileStatus.COMPLETE,
                null,
                true,
                false,
                Set.of(),
                Set.of(authenticatedAppRole(permissions))
        );
    }

    private AuthenticatedAppRole authenticatedAppRole(Set<Permission> permissions) {
        return new AuthenticatedAppRole(
                UUID.randomUUID(),
                "Test Role",
                1,
                null,
                false,
                null,
                null,
                "Test Role Description",
                false,
                null,
                permissions
        );
    }

    private UserProfile buildFullTargetProfile(UserType userType) {
        EntraUser entraUser = EntraUser.builder()
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .multiFirmUser(false)
                .entraOid(UUID.randomUUID().toString())
                .build();

        App app = App.builder()
                .id(UUID.randomUUID())
                .name("Test App")
                .description("Test Application")
                .appType(AppType.LAA)
                .ordinal(1)
                .url("http://test-app.example.com")
                .enabled(true)
                .entraAppId("test-entra-client-id-123")
                .build();

        AppRole role = AppRole.builder()
                .id(UUID.randomUUID())
                .name("Test Role")
                .description("Test Role Description")
                .ordinal(1)
                .ccmsCode("TEST")
                .legacySync(false)
                .authzRole(false)
                .app(app)
                .permissions(Set.of(Permission.VIEW_EXTERNAL_USER))
                .build();

        return UserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(userType)
                .appRoles(Set.of(role))
                .offices(new HashSet<>())
                .firm(null)
                .unrestrictedOfficeAccess(false)
                .lastCcmsSyncSuccessful(true)
                .entraUser(entraUser)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .build();
    }

    private AuthenticatedEntraUser authenticatedTestUser(AuthenticatedUserProfile activeProfile) {
        return new AuthenticatedEntraUser(
                UUID.randomUUID(),
                actorOid,
                "firstName",
                "lastName",
                "email@email.com",
                UserAccountStatus.ACTIVE,
                false,
                LocalDateTime.now(),
                false,
                true,
                null,
                false,
                null,
                InvitationStatus.VERIFICATION_SUCCESS,
                activeProfile == null ? Set.of() : Set.of(activeProfile),
                activeProfile
        );
    }
}
