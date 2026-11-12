package uk.gov.justice.laa.datauserapi.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileAccessDenialReason;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserProfileAccessTarget;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileAccessQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileAccessQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.model.Permission;

@ExtendWith(MockitoExtension.class)
class UserProfileAccessQueryServiceTest {

    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID FIRM_ID = UUID.randomUUID();

    @Mock
    private UserProfileAccessQueryRepository repository;
    @Mock
    private UserProfileAccessQueryMapper mapper;

    private final QueryAuthorisationService authorisationService = new QueryAuthorisationService();

    private UserProfileAccessQueryService service() {
        return new UserProfileAccessQueryService(repository, authorisationService, mapper);
    }

    @Test
    void internalActorWithViewPermissionSeesInternalProfileApps() {
        AppViewList expected = stubActiveApps(UserType.INTERNAL, null);

        AppViewList result = service().getApps(PROFILE_ID, internalActor(Permission.VIEW_INTERNAL_USER));

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findEnabledAppsByProfileId(eq(PROFILE_ID), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(UserProfileAccessQueryService.MAX_APPS);
    }

    @Test
    void internalActorWithViewExternalPermissionSeesExternalProfile() {
        stubActiveApps(UserType.EXTERNAL, FIRM_ID);

        assertThat(service().getApps(PROFILE_ID, internalActor(Permission.VIEW_EXTERNAL_USER)).items()).hasSize(1);
    }

    @Test
    void externalActorSeesProfileInActiveFirm() {
        stubActiveApps(UserType.EXTERNAL, FIRM_ID);

        assertThat(service().getApps(PROFILE_ID, externalActor(FIRM_ID)).items()).hasSize(1);
    }

    @Test
    void externalActorCannotSeeProfileInAnotherFirm() {
        stubTarget(UserType.EXTERNAL, FIRM_ID, false);

        assertAccessDenied(() -> service().getApps(PROFILE_ID, externalActor(UUID.randomUUID())));
    }

    @Test
    void externalActorCannotSeeInternalProfile() {
        stubTarget(UserType.INTERNAL, null, false);

        assertAccessDenied(() -> service().getApps(PROFILE_ID, externalActor(FIRM_ID)));
    }

    @Test
    void internalActorWithoutViewExternalPermissionCannotSeeExternalProfile() {
        stubTarget(UserType.EXTERNAL, FIRM_ID, false);

        assertAccessDenied(() -> service().getApps(PROFILE_ID, internalActor(Permission.VIEW_INTERNAL_USER)));
    }

    @Test
    void missingAndInvisibleProfilesAreDeniedWithServerSideReasonsOnly() {
        when(repository.findAccessTargetById(PROFILE_ID)).thenReturn(Optional.empty());
        Throwable missing = org.assertj.core.api.Assertions.catchThrowable(
                () -> service().getOffices(PROFILE_ID, externalActor(FIRM_ID)));

        UUID otherProfile = UUID.randomUUID();
        when(repository.findAccessTargetById(otherProfile))
                .thenReturn(Optional.of(new UserProfileAccessTarget(UserType.EXTERNAL, UUID.randomUUID(), false)));
        Throwable invisible = org.assertj.core.api.Assertions.catchThrowable(
                () -> service().getOffices(otherProfile, externalActor(FIRM_ID)));

        assertThat(missing).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(ProfileAccessDenialReason.PROFILE_NOT_FOUND.name());
        assertThat(invisible).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(ProfileAccessDenialReason.PROFILE_NOT_VISIBLE.name());
    }

    @Test
    void appRolesForwardBothFiltersAndCapResults() {
        UUID appId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        stubTarget(UserType.EXTERNAL, FIRM_ID, false);
        List<AppRole> entities = List.of(mock(AppRole.class));
        List<AppRoleView> views = List.of(mock(AppRoleView.class));
        when(repository.findAppRolesByProfileId(eq(PROFILE_ID), eq(appId), eq(clientId.toString()), any()))
                .thenReturn(entities);
        when(mapper.toAppRoleViews(entities)).thenReturn(views);

        AppRoleViewList result = service().getAppRoles(PROFILE_ID, appId, clientId, externalActor(FIRM_ID));

        assertThat(result.items()).isSameAs(views);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAppRolesByProfileId(eq(PROFILE_ID), eq(appId), eq(clientId.toString()), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(UserProfileAccessQueryService.MAX_APP_ROLES);
    }

    @Test
    void appRolesWithoutFiltersPassNulls() {
        stubTarget(UserType.EXTERNAL, FIRM_ID, false);
        when(repository.findAppRolesByProfileId(eq(PROFILE_ID), isNull(), isNull(), any())).thenReturn(List.of());
        when(mapper.toAppRoleViews(List.of())).thenReturn(List.of());

        assertThat(service().getAppRoles(PROFILE_ID, null, null, externalActor(FIRM_ID)).items()).isEmpty();
    }

    @Test
    void unrestrictedOfficeAccessReturnsAllFirmOffices() {
        stubTarget(UserType.EXTERNAL, FIRM_ID, true);
        List<OfficeView> offices = List.of(office());
        when(repository.findOfficesByFirmId(eq(FIRM_ID), any())).thenReturn(offices);

        OfficeViewList result = service().getOffices(PROFILE_ID, externalActor(FIRM_ID));

        assertThat(result.items()).isEqualTo(offices);
        verify(repository).findAccessTargetById(PROFILE_ID);
        verify(repository).findOfficesByFirmId(eq(FIRM_ID), any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void restrictedOfficeAccessReturnsOnlyAssignedOfficesWithinTheProfileFirm() {
        stubTarget(UserType.EXTERNAL, FIRM_ID, false);
        List<OfficeView> offices = List.of(office());
        when(repository.findAssignedOfficesByProfileId(eq(PROFILE_ID), eq(FIRM_ID), any())).thenReturn(offices);

        OfficeViewList result = service().getOffices(PROFILE_ID, externalActor(FIRM_ID));

        assertThat(result.items()).isEqualTo(offices);
        verify(repository).findAssignedOfficesByProfileId(eq(PROFILE_ID), eq(FIRM_ID), any());
    }

    @Test
    void officesAreEmptyForInternalProfileWithoutFirm() {
        stubTarget(UserType.INTERNAL, null, true);

        OfficeViewList result = service().getOffices(PROFILE_ID, internalActor(Permission.VIEW_INTERNAL_USER));

        assertThat(result.items()).isEmpty();
    }

    @Test
    void emptyResultsAreReturnedAsEmptyLists() {
        stubTarget(UserType.EXTERNAL, FIRM_ID, false);
        when(repository.findEnabledAppsByProfileId(eq(PROFILE_ID), any())).thenReturn(List.of());
        when(repository.findAssignedOfficesByProfileId(eq(PROFILE_ID), eq(FIRM_ID), any())).thenReturn(List.of());

        assertThat(service().getApps(PROFILE_ID, externalActor(FIRM_ID)).items()).isEmpty();
        assertThat(service().getOffices(PROFILE_ID, externalActor(FIRM_ID)).items()).isEmpty();
    }

    private void assertAccessDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(AccessDeniedException.class);
    }

    private AppViewList stubActiveApps(UserType type, UUID firmId) {
        stubTarget(type, firmId, false);
        AppView app = new AppView(UUID.randomUUID().toString(), "App", "Description", "https://app", true, 1,
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
        when(repository.findEnabledAppsByProfileId(eq(PROFILE_ID), any())).thenReturn(List.of(app));
        return new AppViewList(List.of(app));
    }

    private void stubTarget(UserType type, UUID firmId, boolean unrestricted) {
        when(repository.findAccessTargetById(PROFILE_ID))
                .thenReturn(Optional.of(new UserProfileAccessTarget(type, firmId, unrestricted)));
    }

    private OfficeView office() {
        return new OfficeView(UUID.randomUUID().toString(), FIRM_ID.toString(), "AB1 2CD", "1 Street", null, null, "Town");
    }

    private AuthenticatedEntraUser internalActor(Permission permission) {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .appRoles(Set.of(roleWith(permission)))
                .build();
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }

    private AuthenticatedEntraUser externalActor(UUID firmId) {
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.EXTERNAL)
                .firmId(firmId)
                .appRoles(Set.of())
                .build();
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }

    private AuthenticatedAppRole roleWith(Permission permission) {
        return new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null, "Role",
                true, null, Set.of(permission));
    }
}
