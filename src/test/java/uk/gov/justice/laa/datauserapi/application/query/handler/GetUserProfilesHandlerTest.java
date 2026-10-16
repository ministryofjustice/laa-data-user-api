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
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserProfilesHandlerTest {

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
    void returnsEmptyListWhenUserHasNoProfiles() {
        UserProfile actorProfile = UserProfile.builder()
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .build();
        stubActor(actorProfile);

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(List.of()));

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);
        List<UserProfileSummaryView> result = handler.handle(userEntraObjectId, authenticatedTestUser());

        assertThat(result).isEmpty();
    }

    @Test
    void internalActorCanViewInternalProfiles() {
        UserProfile actorProfile = UserProfile.builder()
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .build();
        stubActor(actorProfile);

        UUID profileId = UUID.randomUUID();
        UserProfileSummaryView profile = new UserProfileSummaryView(
                profileId, true, UserType.INTERNAL, "user@example.com", "John Doe",
                "", "", false, UserAccountStatus.ACTIVE, UserProfileStatus.COMPLETE, true);

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(List.of(profile)));
        when(authorizationService.handle(actorProfile, profile)).thenReturn(true);

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);
        List<UserProfileSummaryView> result = handler.handle(userEntraObjectId, authenticatedTestUser());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userType()).isEqualTo(UserType.INTERNAL);
    }

    @Test
    void externalActorCannotViewOtherFirmProfiles() {
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

        UUID targetFirmId = UUID.randomUUID();
        UserProfileSummaryView profile = new UserProfileSummaryView(
                UUID.randomUUID(), true, UserType.EXTERNAL, "user@example.com", "John Doe",
                "Firm B", targetFirmId.toString(), false, UserAccountStatus.ACTIVE, UserProfileStatus.COMPLETE, true);

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(List.of(profile)));
        when(authorizationService.handle(actorProfile, profile)).thenReturn(false);

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);
        List<UserProfileSummaryView> result = handler.handle(userEntraObjectId, authenticatedTestUser());

        assertThat(result).isEmpty();
    }

    @Test
    void internalActorWithViewExternalUserCanViewExternalProfiles() {
        AppRole viewExternalRole = AppRole.builder()
                .permissions(Set.of(Permission.VIEW_EXTERNAL_USER))
                .build();
        UserProfile actorProfile = UserProfile.builder()
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .appRoles(Set.of(viewExternalRole))
                .build();
        stubActor(actorProfile);

        UUID profileId = UUID.randomUUID();
        UserProfileSummaryView profile = new UserProfileSummaryView(
                profileId, true, UserType.EXTERNAL, "external@example.com", "Jane Doe",
                "External Firm", UUID.randomUUID().toString(), false, UserAccountStatus.ACTIVE, UserProfileStatus.COMPLETE, false);

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(List.of(profile)));
        when(authorizationService.handle(actorProfile, profile)).thenReturn(true);

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);
        List<UserProfileSummaryView> result = handler.handle(userEntraObjectId, authenticatedTestUser());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userType()).isEqualTo(UserType.EXTERNAL);
    }

    @Test
    void throwsExceptionWhenActorHasNoActiveProfile() {
        EntraUserDto actor = EntraUserDto.builder().userProfiles(Set.of()).build();
        when(userAccountQueryService.findByEntraOid(actorOid)).thenReturn(actor);

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, userAccountQueryService, authorizationService);

        assertThatThrownBy(() -> handler.handle(userEntraObjectId, authenticatedTestUser()))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessageContaining("Actor has no active profile");
    }

    private void stubActor(UserProfile actorProfile) {
        EntraUserDto actor = EntraUserDto.builder().userProfiles(Set.of(actorProfile)).build();
        when(userAccountQueryService.findByEntraOid(actorOid)).thenReturn(actor);
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
