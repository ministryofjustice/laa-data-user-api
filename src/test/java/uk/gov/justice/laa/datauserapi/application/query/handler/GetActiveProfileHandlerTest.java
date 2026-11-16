package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import uk.gov.justice.laa.datauserapi.application.query.service.QueryAuthorisationService;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
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
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundReason;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GetActiveProfileHandlerTest {

    @Mock
    private UserProfileQueryRepository userProfileQueryRepository;

    @Spy
    private QueryAuthorisationService authorizationService = new QueryAuthorisationService();

    private ModelMapper modelMapper;

    private String actorOid;
    private String userEntraObjectId;

    @BeforeEach
    void setUp() {
        actorOid = UUID.randomUUID().toString();
        userEntraObjectId = UUID.randomUUID().toString();
        modelMapper = new ModelMapper();
    }

    @Test
    void delegatesToProfileService() {
        UserProfileDetailsService service = mock(UserProfileDetailsService.class);
        AuthenticatedEntraUser actor = authenticatedTestUser(null);
        UserProfileDetailView expected = mock(UserProfileDetailView.class);
        when(service.getActiveProfile(userEntraObjectId, actor)).thenReturn(expected);

        assertThat(new GetActiveProfileHandler(service).handle(userEntraObjectId, actor)).isSameAs(expected);
        verify(service).getActiveProfile(userEntraObjectId, actor);
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

        GetActiveProfileHandler handler = handler();
        UserProfileDetailView result = handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile));

        assertThat(result).isNotNull();
        assertThat(result.userType()).isEqualTo(UserType.INTERNAL);
        assertThat(result.roles()).isNotEmpty();
        assertThat(result.roles().getFirst().appEntraClientId()).isEqualTo("test-entra-client-id-123");
    }

    @Test
    void throwsNotFoundWhenProfileDoesNotExist() {
        AuthenticatedUserProfile actorProfile = authenticatedUserProfile(
                UserType.INTERNAL,
                null,
                Set.of(Permission.VIEW_INTERNAL_USER));

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId))
                .thenReturn(Optional.empty());

        GetActiveProfileHandler handler = handler();

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile)))
                .isInstanceOfSatisfying(UserProfileNotFoundException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(UserProfileNotFoundReason.ACTIVE_PROFILE_NOT_FOUND))
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

        GetActiveProfileHandler handler = handler();

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile)))
                .isInstanceOfSatisfying(UserProfileNotFoundException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(UserProfileNotFoundReason.ACTIVE_PROFILE_NOT_FOUND))
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

        GetActiveProfileHandler handler = handler();
        UserProfileDetailView result = handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile));

        assertThat(result).isNotNull();
        assertThat(result.userType()).isEqualTo(UserType.EXTERNAL);
        assertThat(result.roles()).isNotEmpty();
        assertThat(result.roles().getFirst().appEntraClientId()).isEqualTo("test-entra-client-id-123");
    }

    @Test
    void throwsExceptionWhenActorHasNoActiveProfile() {
        GetActiveProfileHandler handler = handler();

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser(null)))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessageContaining("Actor has no active profile");
        verifyNoInteractions(userProfileQueryRepository);
    }

    @Test
    void rejectsUndeterminedActorTypeBeforeProfileLookup() {
        AuthenticatedUserProfile profile = authenticatedUserProfile(null, null, Set.of());

        assertThatThrownBy(() -> handler().handle(userEntraObjectId, authenticatedTestUser(profile)))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessage("Actor user type cannot be determined");
        verifyNoInteractions(userProfileQueryRepository);
    }

    private GetActiveProfileHandler handler() {
        return new GetActiveProfileHandler(new UserProfileDetailsService(userProfileQueryRepository,
                authorizationService, new UserProfileQueryMapper(modelMapper), new GetActiveProfileQueryMapper()));
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
