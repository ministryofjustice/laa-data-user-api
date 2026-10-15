package uk.gov.justice.laa.datauserapi.application.query.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.contracts.dto.FirmSearchView;
import uk.gov.justice.laa.datauserapi.contracts.dto.FirmView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchViewList;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmViewPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.service.FirmQueryService;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.response.FirmOfficesResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.FirmPageResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.FirmResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.FirmSearchResponse;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirmQueryControllerTest {

    @Mock
    private FirmQueryService firmQueryService;

    private FirmQueryController controller;

    @Test
    void queryFirms_delegatesToService() {
        controller = new FirmQueryController(firmQueryService);
        FirmView view = new FirmView("123456", "Test Firm", FirmType.LEGAL_SERVICES_PROVIDER, null, true);
        FirmViewPage page = new FirmViewPage(java.util.List.of(view), new PageMetadata(0, 20, 1, 1));
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .build();
        when(firmQueryService.listFirms(eq(user), eq("Test"), eq(0), eq(20), eq("name,asc")))
                .thenReturn(page);

        ResponseEntity<FirmPageResponse> response = controller.queryFirms("Test", 0, 20, "name,asc", user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new FirmPageResponse(page.items(), page.page()));
    }

    @Test
    void queryFirmById_delegatesToService() {
        controller = new FirmQueryController(firmQueryService);
        FirmView view = new FirmView("123456", "Test Firm", FirmType.LEGAL_SERVICES_PROVIDER, null, true);
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .build();
        when(firmQueryService.getFirmById(eq(user), eq("123456"))).thenReturn(view);

        ResponseEntity<FirmResponse> response = controller.queryFirmById("123456", user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new FirmResponse(view));
    }

    @Test
    void queryFirmsSearch_delegatesToService() {
        controller = new FirmQueryController(firmQueryService);
        FirmSearchView searchView = new FirmSearchView("123456", "Test Firm");
        FirmSearchViewList list = new FirmSearchViewList(java.util.List.of(searchView));
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .build();
        when(firmQueryService.searchFirms(eq(user), eq("Test"), eq(10))).thenReturn(list);

        ResponseEntity<FirmSearchResponse> response = controller.queryFirmsSearch("Test", 10, user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new FirmSearchResponse(list.items()));
    }

    @Test
    void queryFirmOffices_delegatesToService() {
        controller = new FirmQueryController(firmQueryService);
        OfficeView office = new OfficeView("00001", "123456", "AB1 2CD", "Line 1", null, null, "City");
        OfficeViewList list = new OfficeViewList(java.util.List.of(office));
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .build();
        when(firmQueryService.getFirmOffices(eq(user), eq("123456"))).thenReturn(list);

        ResponseEntity<FirmOfficesResponse> response = controller.queryFirmOffices("123456", user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new FirmOfficesResponse(list.items()));
    }
}
