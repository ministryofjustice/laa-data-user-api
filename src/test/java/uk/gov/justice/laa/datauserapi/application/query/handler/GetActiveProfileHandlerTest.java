package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.model.UserProfileSilasStatus;

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

    private static final String ACTOR_OID = UUID.randomUUID().toString();
    private static final String USER_ENTRA_OBJECT_ID = UUID.randomUUID().toString();

    @Test
    void returnsActiveProfileWhenAccessible() {
        UserProfile actorProfile = UserProfile.builder()
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .build();
        stubActor(actorProfile);

        UserProfile targetProfile = buildFullTargetProfile(UserType.INTERNAL);
        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(USER_ENTRA_OBJECT_ID))
                .thenReturn(Optional.of(targetProfile));

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService);
        UserProfileDetailView result = handler.handle(new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID));

        assertThat(result).isNotNull();
        assertThat(result.userType()).isEqualTo(UserType.INTERNAL);
    }

    @Test
    void throwsNotFoundWhenProfileDoesNotExist() {
        UserProfile actorProfile = UserProfile.builder()
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .build();
        stubActor(actorProfile);

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(USER_ENTRA_OBJECT_ID))
                .thenReturn(Optional.empty());

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService);

        assertThatThrownBy(() -> handler.handle(new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID)))
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
        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(USER_ENTRA_OBJECT_ID))
                .thenReturn(Optional.of(targetProfile));

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService);

        assertThatThrownBy(() -> handler.handle(new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID)))
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
        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(USER_ENTRA_OBJECT_ID))
                .thenReturn(Optional.of(targetProfile));

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService);
        UserProfileDetailView result = handler.handle(new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID));

        assertThat(result).isNotNull();
        assertThat(result.userType()).isEqualTo(UserType.EXTERNAL);
    }

    @Test
    void throwsExceptionWhenActorHasNoActiveProfile() {
        EntraUserDto actor = EntraUserDto.builder().userProfiles(Set.of()).build();
        when(userAccountQueryService.findByEntraOid(ACTOR_OID)).thenReturn(actor);

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository, userAccountQueryService);

        assertThatThrownBy(() -> handler.handle(new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID)))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessageContaining("Actor has no active profile");
    }

    private void stubActor(UserProfile actorProfile) {
        EntraUserDto actor = EntraUserDto.builder().userProfiles(Set.of(actorProfile)).build();
        when(userAccountQueryService.findByEntraOid(ACTOR_OID)).thenReturn(actor);
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

        return UserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(userType)
                .appRoles(new HashSet<>())
                .offices(new HashSet<>())
                .firm(null)
                .unrestrictedOfficeAccess(false)
                .lastCcmsSyncSuccessful(true)
                .entraUser(entraUser)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .build();
    }
}
