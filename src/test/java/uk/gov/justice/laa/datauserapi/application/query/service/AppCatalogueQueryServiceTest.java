package uk.gov.justice.laa.datauserapi.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileAccessQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AppCatalogueQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.exception.AppNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.AppRoleNotFoundException;

@ExtendWith(MockitoExtension.class)
class AppCatalogueQueryServiceTest {

    private static final UUID APP_ID = UUID.randomUUID();

    @Mock
    private AppCatalogueQueryRepository repository;

    private AppCatalogueQueryService service;
    private App app;

    @BeforeEach
    void setUp() {
        service = new AppCatalogueQueryService(repository, new UserProfileAccessQueryMapper());
        app = App.builder().id(APP_ID).name("App").entraAppId("client-id").build();
    }

    @Test
    void appsAreReadAndCapped() {
        AppView view = new AppView(APP_ID.toString(), "App", "Desc", "https://app", true, 1, "oid", "client-id");
        when(repository.findActiveApps(any())).thenReturn(List.of(view));

        assertThat(service.getApps().items()).containsExactly(view);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findActiveApps(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(AppCatalogueQueryService.MAX_APPS);
    }

    @Test
    void unknownOrInactiveAppIsNotFound() {
        when(repository.findActiveAppById(APP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApp(APP_ID)).isInstanceOf(AppNotFoundException.class);
    }

    @Test
    void rolesOfAnUnknownOrInactiveAppAreNotFound() {
        when(repository.existsByIdAndEnabledTrue(APP_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getAppRoles(APP_ID, null)).isInstanceOf(AppNotFoundException.class);
    }

    @Test
    void activeAppWithoutRolesReturnsAnEmptyList() {
        when(repository.existsByIdAndEnabledTrue(APP_ID)).thenReturn(true);
        when(repository.findRolesOfActiveApp(eq(APP_ID), eq("INTERNAL"), eq("EXTERNAL"), any()))
                .thenReturn(List.of());

        assertThat(service.getAppRoles(APP_ID, UserType.INTERNAL).items()).isEmpty();
    }

    @Test
    void rolesAreMappedAndTheUserTypeItsOppositeAndTheCapArePassedToTheRepository() {
        when(repository.existsByIdAndEnabledTrue(APP_ID)).thenReturn(true);
        when(repository.findRolesOfActiveApp(any(), any(), any(), any())).thenReturn(List.of(
                role(UUID.randomUUID(), "role", new UserType[] {UserType.EXTERNAL})));

        assertThat(service.getAppRoles(APP_ID, UserType.EXTERNAL).items())
                .extracting(AppRoleView::name, AppRoleView::appRoleUserTypeRestriction)
                .containsExactly(tuple("role", "EXTERNAL"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findRolesOfActiveApp(
                eq(APP_ID), eq("EXTERNAL"), eq("INTERNAL"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(AppCatalogueQueryService.MAX_APP_ROLES);
    }

    @Test
    void omittedUserTypeAppliesNoFilter() {
        when(repository.existsByIdAndEnabledTrue(APP_ID)).thenReturn(true);
        when(repository.findRolesOfActiveApp(eq(APP_ID), isNull(), isNull(), any())).thenReturn(List.of());

        assertThat(service.getAppRoles(APP_ID, null).items()).isEmpty();
    }

    @Test
    void roleOfAnActiveAppIsMappedWithItsAppIdentifiers() {
        UUID roleId = UUID.randomUUID();
        when(repository.findRoleOfActiveAppById(roleId)).thenReturn(Optional.of(role(roleId, "role", null)));

        AppRoleView view = service.getAppRole(roleId);

        assertThat(view.appRoleId()).isEqualTo(roleId.toString());
        assertThat(view.appId()).isEqualTo(APP_ID.toString());
        assertThat(view.appEntraClientId()).isEqualTo("client-id");
    }

    @Test
    void unknownOrIneligibleRoleIsNotFound() {
        UUID roleId = UUID.randomUUID();
        when(repository.findRoleOfActiveAppById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAppRole(roleId)).isInstanceOf(AppRoleNotFoundException.class);
    }

    private AppRole role(UUID id, String name, UserType[] userTypes) {
        return AppRole.builder().id(id).name(name).description(name).roleIdentifier(name).app(app)
                .userTypeRestriction(userTypes).build();
    }
}
