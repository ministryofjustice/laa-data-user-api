package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
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
    private UserAccountQueryService userAccountQueryService;

    @Mock
    private QueryProfileAuthorisationHandler authorizationService;

    private String actorOid;
    private String userEntraObjectId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        actorOid = UUID.randomUUID().toString();
        userEntraObjectId = UUID.randomUUID().toString();
    }

    @Test
    void returnsActiveProfileWhenAccessible() {
        UserProfile actorProfile = UserProfile.builder()
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .build();
        stubActor(actorProfile);

        UserProfile targetProfile = buildFullTargetProfile(UserType.INTERNAL);
        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId))
                .thenReturn(Optional.of(targetProfile));
        when(authorizationService.handle(actorProfile, targetProfile)).thenReturn(true);

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);
        UserProfileDetailView result = handler.handle(userEntraObjectId, authenticatedTestUser());

        assertThat(result).isNotNull();
        assertThat(result.userType()).isEqualTo(UserType.INTERNAL);
        assertThat(result.roles()).isNotEmpty();
        assertThat(result.roles().get(0).appEntraClientId()).isEqualTo("test-entra-client-id-123");
    }

    @Test
    void throwsNotFoundWhenProfileDoesNotExist() {
        UserProfile actorProfile = UserProfile.builder()
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .build();
        stubActor(actorProfile);

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId))
                .thenReturn(Optional.empty());

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser()))
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
        UserProfile actorProfile = UserProfile.builder()
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firm(actorFirm)
                .appRoles(new HashSet<>())
                .build();
        stubActor(actorProfile);

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
        when(authorizationService.handle(actorProfile, targetProfile)).thenReturn(false);

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active profile not found");
    }

    @Test
    void internalActorWithViewExternalUserCanViewExternalProfile() {
        AppRole viewExternalRole = AppRole.builder()
                .permissions(Set.of(Permission.VIEW_EXTERNAL_USER))
                .build();
        UserProfile actorProfile = UserProfile.builder()
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .appRoles(Set.of(viewExternalRole))
                .build();
        stubActor(actorProfile);

        UserProfile targetProfile = buildFullTargetProfile(UserType.EXTERNAL);
        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(userEntraObjectId))
                .thenReturn(Optional.of(targetProfile));
        when(authorizationService.handle(actorProfile, targetProfile)).thenReturn(true);

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);
        UserProfileDetailView result = handler.handle(userEntraObjectId, authenticatedTestUser());

        assertThat(result).isNotNull();
        assertThat(result.userType()).isEqualTo(UserType.EXTERNAL);
        assertThat(result.roles()).isNotEmpty();
        assertThat(result.roles().get(0).appEntraClientId()).isEqualTo("test-entra-client-id-123");
    }

    @Test
    void throwsExceptionWhenActorHasNoActiveProfile() {
        EntraUserDto actor = EntraUserDto.builder().userProfiles(Set.of()).build();
        when(userAccountQueryService.findByEntraOid(actorOid)).thenReturn(actor);

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser()))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessageContaining("Actor has no active profile");
    }

    private void stubActor(UserProfile actorProfile) {
        EntraUserDto actor = EntraUserDto.builder().userProfiles(Set.of(actorProfile)).build();
        when(userAccountQueryService.findByEntraOid(actorOid)).thenReturn(actor);
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

    private AuthenticatedEntraUser authenticatedTestUser() {
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
                Set.of(),
                null
        );
    }
}
