package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import uk.gov.justice.laa.datauserapi.contracts.dto.FirmSearchView;
import uk.gov.justice.laa.datauserapi.contracts.dto.FirmView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchAccess;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmViewPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.FirmQueryRepository;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.OfficeQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;

@ExtendWith(MockitoExtension.class)
class FirmQueryServiceTest {

    @Mock
    private FirmQueryRepository firmQueryRepository;

    @Mock
    private OfficeQueryRepository officeQueryRepository;

    @Mock
    private FirmAuthorisationService authorisationService;

    private FirmQueryService firmQueryService;

    private final AuthenticatedEntraUser authenticatedUser =
            authenticatedUser(UserType.INTERNAL, null, Permission.VIEW_FIRM_DIRECTORY);

    @Test
    void listFirms_returnsMappedPage() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        FirmView view = new FirmView("123456", "Test Firm", FirmType.LEGAL_SERVICES_PROVIDER, null, true);
        Page<FirmView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 20), 1);
        when(firmQueryRepository.search(eq("Test"), any(Pageable.class))).thenReturn(page);

        FirmViewPage result = firmQueryService.listFirms(authenticatedUser, "Test", null, 0, 20, "name,asc");

        verify(authorisationService).requireFirmDirectoryAccess(authenticatedUser);
        assertThat(result.items()).containsExactly(view);
        assertThat(result.page().totalElements()).isEqualTo(1);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(firmQueryRepository).search(eq("Test"), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("name").isIgnoreCase()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("code").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listFirms_usesEmptySearchTerm_whenFirmFilterIsMissing() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        when(firmQueryRepository.search(eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        firmQueryService.listFirms(authenticatedUser, null, null, 0, 20, null);
        firmQueryService.listFirms(authenticatedUser, null, null, 0, 20, "");

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(firmQueryRepository, times(2)).search(eq(""), pageable.capture());
        assertThat(pageable.getAllValues()).allSatisfy(request -> {
            assertThat(request.getSort().getOrderFor("name").isIgnoreCase()).isTrue();
            assertThat(request.getSort().getOrderFor("code").getDirection()).isEqualTo(Sort.Direction.ASC);
        });
    }

    @Test
    void listFirms_mapsSupportedSortFieldsToEntityProperties() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        when(firmQueryRepository.search(eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        List.of("code,desc", "code,asc", "name,asc", "type,desc")
                .forEach(sortBy -> firmQueryService.listFirms(authenticatedUser, null, null, 0, 20, sortBy));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(firmQueryRepository, times(4)).search(eq(""), pageable.capture());
        assertThat(pageable.getAllValues().stream()
                .map(value -> value.getSort().iterator().next().getProperty()))
                .containsExactly("code", "code", "name", "type");
        assertThat(pageable.getAllValues().stream()
                .map(value -> value.getSort().iterator().next().getDirection()))
                .containsExactly(
                        Sort.Direction.DESC,
                        Sort.Direction.ASC,
                        Sort.Direction.ASC,
                        Sort.Direction.DESC);
        assertThat(pageable.getAllValues().stream()
                .map(value -> value.getSort().getOrderFor("code").getDirection()))
                .containsExactly(
                        Sort.Direction.DESC,
                        Sort.Direction.ASC,
                        Sort.Direction.ASC,
                        Sort.Direction.DESC);
        assertThat(pageable.getAllValues().stream()
                .map(value -> value.getSort().stream().count()))
                .containsExactly(1L, 1L, 2L, 2L);
    }

    @Test
    void listFirms_rejectsUnsupportedSortProperty() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);

        assertThatThrownBy(() -> firmQueryService.listFirms(authenticatedUser, null, null, 0, 20, "unknown,asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported firm sort value");
    }

    @Test
    void listFirms_filtersByFirmType_whenProvided() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        when(firmQueryRepository.searchByFirmType(eq("Barristers"), eq(FirmType.CHAMBERS), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        firmQueryService.listFirms(authenticatedUser, "Barristers", "CHAMBERS", 0, 20, "type,desc");

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(firmQueryRepository).searchByFirmType(eq("Barristers"), eq(FirmType.CHAMBERS), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("type").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("code").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listFirms_rejectsUnsupportedFirmTypeValue() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);

        assertThatThrownBy(() -> firmQueryService.listFirms(authenticatedUser, null, "NOT_A_TYPE", 0, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported firm type value");
    }

    @Test
    void listFirms_throws_whenAuthorisationDenies() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(authorisationService).requireFirmDirectoryAccess(authenticatedUser);

        assertThatThrownBy(() -> firmQueryService.listFirms(authenticatedUser, null, null, 0, 20, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getFirmById_returnsFirmView_whenFound() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        FirmView view = new FirmView("123456", "Test Firm", FirmType.LEGAL_SERVICES_PROVIDER, null, true);
        when(firmQueryRepository.findViewByCode("123456")).thenReturn(Optional.of(view));

        FirmView result = firmQueryService.getFirmById(authenticatedUser, "123456");

        assertThat(result.firmId()).isEqualTo("123456");
        assertThat(result.name()).isEqualTo("Test Firm");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void getFirmById_throwsNotFound_whenMissing() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        when(firmQueryRepository.findViewByCode("999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> firmQueryService.getFirmById(authenticatedUser, "999999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchFirms_appliesFirmCodeRestriction_forExternalUser() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        when(authorisationService.resolveSearchAccess(authenticatedUser)).thenReturn(new FirmSearchAccess(false, "123456"));
        FirmSearchView searchView = new FirmSearchView("123456", "Test Firm");
        when(firmQueryRepository.searchTypeAhead(anyString(), eq(false), eq("123456"), any(Pageable.class)))
                .thenReturn(List.of(searchView));

        var result = firmQueryService.searchFirms(authenticatedUser, "Test", 10);

        assertThat(result.items()).containsExactly(searchView);
    }

    @Test
    void searchFirms_noRestriction_forInternalUser() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        when(authorisationService.resolveSearchAccess(authenticatedUser)).thenReturn(new FirmSearchAccess(true, null));
        when(firmQueryRepository.searchTypeAhead(anyString(), eq(true), org.mockito.ArgumentMatchers.isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        var result = firmQueryService.searchFirms(authenticatedUser, "Test", 10);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchFirms_usesEmptyTerm_whenQueryIsNull() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        when(authorisationService.resolveSearchAccess(authenticatedUser)).thenReturn(new FirmSearchAccess(true, null));
        when(firmQueryRepository.searchTypeAhead(eq(""), eq(true), org.mockito.ArgumentMatchers.isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        firmQueryService.searchFirms(authenticatedUser, null, 10);

        verify(firmQueryRepository).searchTypeAhead(eq(""), eq(true), org.mockito.ArgumentMatchers.isNull(), any(Pageable.class));
    }

    @Test
    void getFirmOffices_returnsEmptyList_whenFirmHasNoOffices() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        when(firmQueryRepository.existsByCode("123456")).thenReturn(true);
        when(officeQueryRepository.findByFirmCode("123456")).thenReturn(List.of());

        OfficeViewList result = firmQueryService.getFirmOffices(authenticatedUser, "123456");

        assertThat(result.items()).isEmpty();
    }

    @Test
    void getFirmOffices_returnsOffices_whenPresent() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        OfficeView officeView = new OfficeView("00001", "123456", "AB1 2CD", "Line 1", null, null, "City");
        when(firmQueryRepository.existsByCode("123456")).thenReturn(true);
        when(officeQueryRepository.findByFirmCode("123456")).thenReturn(List.of(officeView));

        OfficeViewList result = firmQueryService.getFirmOffices(authenticatedUser, "123456");

        assertThat(result.items()).containsExactly(officeView);
    }

    @Test
    void getFirmOffices_throwsNotFound_whenFirmMissing() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorisationService);
        when(firmQueryRepository.existsByCode("999999")).thenReturn(false);

        assertThatThrownBy(() -> firmQueryService.getFirmOffices(authenticatedUser, "999999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static AuthenticatedEntraUser authenticatedUser(
            UserType userType,
            String firmCode,
            Permission... permissions) {
        Set<Permission> grantedPermissions = Set.of(permissions);
        Set<AuthenticatedAppRole> roles = grantedPermissions.isEmpty()
                ? Set.of()
                : Set.of(new AuthenticatedAppRole(
                        null, null, 0, null, false, null, null, null, false, null, grantedPermissions));
        AuthenticatedUserProfile profile = new AuthenticatedUserProfile(
                null, true, userType, null, null, null, firmCode, null, null, false, false, Set.of(), roles);
        return AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .activeProfile(profile)
                .build();
    }
}
