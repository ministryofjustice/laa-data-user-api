package uk.gov.justice.laa.datauserapi.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserProfileAccessTarget;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileAccessQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileAccessQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.entity.AppRole;

@ExtendWith(MockitoExtension.class)
class UserProfileAccessQueryServiceTest {

    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID FIRM_ID = UUID.randomUUID();

    @Mock
    private UserProfileAccessQueryRepository repository;
    @Mock
    private UserProfileAccessQueryMapper mapper;

    private UserProfileAccessQueryService service() {
        return new UserProfileAccessQueryService(repository, mapper);
    }

    @Test
    void appsAreReadForTheRequestedProfileAndCapped() {
        AppView app = new AppView(UUID.randomUUID().toString(), "App", "Description", "https://app", true, 1,
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
        when(repository.findEnabledAppsByProfileId(eq(PROFILE_ID), any())).thenReturn(List.of(app));

        AppViewList result = service().getApps(PROFILE_ID);

        assertThat(result.items()).containsExactly(app);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findEnabledAppsByProfileId(eq(PROFILE_ID), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(UserProfileAccessQueryService.MAX_APPS);
    }

    @Test
    void appRolesForwardBothFiltersAndCapResults() {
        UUID appId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        List<AppRole> entities = List.of(mock(AppRole.class));
        List<AppRoleView> views = List.of(mock(AppRoleView.class));
        when(repository.findAppRolesByProfileId(eq(PROFILE_ID), eq(appId), eq(clientId.toString()), any()))
                .thenReturn(entities);
        when(mapper.toAppRoleViews(entities)).thenReturn(views);

        AppRoleViewList result = service().getAppRoles(PROFILE_ID, appId, clientId);

        assertThat(result.items()).isSameAs(views);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAppRolesByProfileId(eq(PROFILE_ID), eq(appId), eq(clientId.toString()), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(UserProfileAccessQueryService.MAX_APP_ROLES);
    }

    @Test
    void appRolesWithoutFiltersPassNulls() {
        when(repository.findAppRolesByProfileId(eq(PROFILE_ID), isNull(), isNull(), any())).thenReturn(List.of());
        when(mapper.toAppRoleViews(List.of())).thenReturn(List.of());

        assertThat(service().getAppRoles(PROFILE_ID, null, null).items()).isEmpty();
    }

    @Test
    void unrestrictedOfficeAccessReturnsAllFirmOffices() {
        stubTarget(UserType.EXTERNAL, FIRM_ID, true);
        List<OfficeView> offices = List.of(office());
        when(repository.findOfficesByFirmId(eq(FIRM_ID), any())).thenReturn(offices);

        OfficeViewList result = service().getOffices(PROFILE_ID);

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

        OfficeViewList result = service().getOffices(PROFILE_ID);

        assertThat(result.items()).isEqualTo(offices);
        verify(repository).findAssignedOfficesByProfileId(eq(PROFILE_ID), eq(FIRM_ID), any());
    }

    @Test
    void officesAreEmptyForInternalProfileWithoutFirm() {
        stubTarget(UserType.INTERNAL, null, true);

        assertThat(service().getOffices(PROFILE_ID).items()).isEmpty();
    }

    @Test
    void officesAreEmptyWhenTheProfileDisappearsAfterAuthorisation() {
        when(repository.findAccessTargetById(PROFILE_ID)).thenReturn(Optional.empty());

        assertThat(service().getOffices(PROFILE_ID).items()).isEmpty();
        verifyNoMoreInteractions(repository);
    }

    private void stubTarget(UserType type, UUID firmId, boolean unrestricted) {
        when(repository.findAccessTargetById(PROFILE_ID))
                .thenReturn(Optional.of(new UserProfileAccessTarget(type, firmId, unrestricted)));
    }

    private OfficeView office() {
        return new OfficeView(UUID.randomUUID().toString(), FIRM_ID.toString(), "AB1 2CD", "1 Street", null, null, "Town");
    }
}
