package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchAccess;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchView;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmView;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmViewPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.FirmQueryRepository;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.OfficeQueryRepository;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.FirmType;

@ExtendWith(MockitoExtension.class)
class FirmQueryServiceTest {

    @Mock
    private FirmQueryRepository firmQueryRepository;

    @Mock
    private OfficeQueryRepository officeQueryRepository;

    @Mock
    private FirmDirectoryAuthorizationService authorizationService;

    private FirmQueryService firmQueryService;

    private final UUID actorId = UUID.randomUUID();

    @Test
    void listFirms_returnsMappedPage() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        FirmView view = new FirmView("123456", "Test Firm", FirmType.LEGAL_SERVICES_PROVIDER, null, true);
        Page<FirmView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 20), 1);
        when(firmQueryRepository.search(eq("Test"), any(Pageable.class))).thenReturn(page);

        FirmViewPage result = firmQueryService.listFirms(actorId, "Test", 0, 20);

        verify(authorizationService).requireFirmDirectoryAccess(actorId);
        assertThat(result.items()).containsExactly(view);
        assertThat(result.page().totalElements()).isEqualTo(1);
    }

    @Test
    void listFirms_usesEmptySearchTerm_whenFirmFilterIsMissing() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        when(firmQueryRepository.search(eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        firmQueryService.listFirms(actorId, null, 0, 20);

        verify(firmQueryRepository).search(eq(""), any(Pageable.class));
    }

    @Test
    void listFirms_throws_whenAuthorizationDenies() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(authorizationService).requireFirmDirectoryAccess(actorId);

        assertThatThrownBy(() -> firmQueryService.listFirms(actorId, null, 0, 20))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getFirmById_returnsFirmView_whenFound() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        FirmView view = new FirmView("123456", "Test Firm", FirmType.LEGAL_SERVICES_PROVIDER, null, true);
        when(firmQueryRepository.findViewByCode("123456")).thenReturn(Optional.of(view));

        FirmView result = firmQueryService.getFirmById(actorId, "123456");

        assertThat(result.firmId()).isEqualTo("123456");
        assertThat(result.name()).isEqualTo("Test Firm");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void getFirmById_throwsNotFound_whenMissing() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        when(firmQueryRepository.findViewByCode("999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> firmQueryService.getFirmById(actorId, "999999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchFirms_appliesFirmCodeRestriction_forExternalUser() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        when(authorizationService.resolveSearchAccess(actorId)).thenReturn(new FirmSearchAccess(false, "123456"));
        FirmSearchView searchView = new FirmSearchView("123456", "Test Firm");
        when(firmQueryRepository.searchTypeAhead(anyString(), eq(false), eq("123456"), any(Pageable.class)))
                .thenReturn(List.of(searchView));

        var result = firmQueryService.searchFirms(actorId, "Test", 10);

        assertThat(result.items()).containsExactly(searchView);
    }

    @Test
    void searchFirms_noRestriction_forInternalUser() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        when(authorizationService.resolveSearchAccess(actorId)).thenReturn(new FirmSearchAccess(true, null));
        when(firmQueryRepository.searchTypeAhead(anyString(), eq(true), org.mockito.ArgumentMatchers.isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        var result = firmQueryService.searchFirms(actorId, "Test", 10);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchFirms_usesEmptyTerm_whenQueryIsNull() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        when(authorizationService.resolveSearchAccess(actorId)).thenReturn(new FirmSearchAccess(true, null));
        when(firmQueryRepository.searchTypeAhead(eq(""), eq(true), org.mockito.ArgumentMatchers.isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        firmQueryService.searchFirms(actorId, null, 10);

        verify(firmQueryRepository).searchTypeAhead(eq(""), eq(true), org.mockito.ArgumentMatchers.isNull(), any(Pageable.class));
    }

    @Test
    void getFirmOffices_returnsEmptyList_whenFirmHasNoOffices() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        when(firmQueryRepository.existsByCode("123456")).thenReturn(true);
        when(officeQueryRepository.findByFirmCode("123456")).thenReturn(List.of());

        OfficeViewList result = firmQueryService.getFirmOffices(actorId, "123456");

        assertThat(result.items()).isEmpty();
    }

    @Test
    void getFirmOffices_returnsOffices_whenPresent() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        OfficeView officeView = new OfficeView("00001", "123456", "AB1 2CD", "Line 1", null, null, "City");
        when(firmQueryRepository.existsByCode("123456")).thenReturn(true);
        when(officeQueryRepository.findByFirmCode("123456")).thenReturn(List.of(officeView));

        OfficeViewList result = firmQueryService.getFirmOffices(actorId, "123456");

        assertThat(result.items()).containsExactly(officeView);
    }

    @Test
    void getFirmOffices_throwsNotFound_whenFirmMissing() {
        firmQueryService = new FirmQueryService(firmQueryRepository, officeQueryRepository, authorizationService);
        when(firmQueryRepository.existsByCode("999999")).thenReturn(false);

        assertThatThrownBy(() -> firmQueryService.getFirmOffices(actorId, "999999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
