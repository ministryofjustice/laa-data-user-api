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
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.time.LocalDateTime;
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
    private QueryAuthorisationService authorizationService;

    private String actorOid;
    private String userEntraObjectId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        actorOid = UUID.randomUUID().toString();
        userEntraObjectId = UUID.randomUUID().toString();
    }

    @Test
    void returnsEmptyListWhenUserHasNoProfiles() {
        AuthenticatedUserProfile actorProfile = authenticatedUserProfile(
                UserType.INTERNAL,
                null,
                Set.of(Permission.VIEW_INTERNAL_USER));

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(List.of()));

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, authorizationService);
        List<UserProfileSummaryView> result = handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile));

        assertThat(result).isEmpty();
    }

    @Test
    void internalActorCanViewInternalProfiles() {
        AuthenticatedUserProfile actorProfile = authenticatedUserProfile(
                UserType.INTERNAL,
                null,
                Set.of(Permission.VIEW_INTERNAL_USER));

        UUID profileId = UUID.randomUUID();
        UserProfileSummaryView profile = new UserProfileSummaryView(
                profileId, true, UserType.INTERNAL, "user@example.com", "John Doe",
                "", "", false, UserAccountStatus.ACTIVE, UserProfileStatus.COMPLETE, true);

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(List.of(profile)));
        when(authorizationService.canViewProfile(actorProfile, profile.userType(), profile.firmId())).thenReturn(true);

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, authorizationService);
        List<UserProfileSummaryView> result = handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile));

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
        AuthenticatedUserProfile actorProfile = authenticatedUserProfile(
                UserType.EXTERNAL,
                actorFirm.getId(),
                Set.of());

        UUID targetFirmId = UUID.randomUUID();
        UserProfileSummaryView profile = new UserProfileSummaryView(
                UUID.randomUUID(), true, UserType.EXTERNAL, "user@example.com", "John Doe",
                "Firm B", targetFirmId.toString(), false, UserAccountStatus.ACTIVE, UserProfileStatus.COMPLETE, true);

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(List.of(profile)));
        when(authorizationService.canViewProfile(actorProfile, profile.userType(), profile.firmId())).thenReturn(false);

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, authorizationService);
        List<UserProfileSummaryView> result = handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile));

        assertThat(result).isEmpty();
    }

    @Test
    void internalActorWithViewExternalUserCanViewExternalProfiles() {
        AuthenticatedUserProfile actorProfile = authenticatedUserProfile(
                UserType.INTERNAL,
                null,
                Set.of(Permission.VIEW_EXTERNAL_USER));

        UUID profileId = UUID.randomUUID();
        UserProfileSummaryView profile = new UserProfileSummaryView(
                profileId, true, UserType.EXTERNAL, "external@example.com", "Jane Doe",
                "External Firm", UUID.randomUUID().toString(), false, UserAccountStatus.ACTIVE, UserProfileStatus.COMPLETE, false);

        when(userProfileQueryRepository.findAllProfilesByUserEntraObjectId(userEntraObjectId))
                .thenReturn(Optional.of(List.of(profile)));
        when(authorizationService.canViewProfile(actorProfile, profile.userType(), profile.firmId())).thenReturn(true);

        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, authorizationService);
        List<UserProfileSummaryView> result = handler.handle(userEntraObjectId, authenticatedTestUser(actorProfile));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userType()).isEqualTo(UserType.EXTERNAL);
    }

    @Test
    void throwsExceptionWhenActorHasNoActiveProfile() {
        GetUserProfilesHandler handler = new GetUserProfilesHandler(userProfileQueryRepository, authorizationService);

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
