package uk.gov.justice.laa.datauserapi.application.query.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchViewList;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmView;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmViewPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.service.FirmQueryService;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.model.FirmType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirmQueryControllerTest {

    @Mock
    private FirmQueryService firmQueryService;

    private FirmQueryController controller;

    private Jwt jwtWithOid(UUID oid) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("oid", oid.toString())
                .subject("test-sub")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }

    @Test
    void queryFirms_delegatesToService() {
        controller = new FirmQueryController(firmQueryService);
        UUID oid = UUID.randomUUID();
        FirmViewPage page = new FirmViewPage(java.util.List.of(), new PageMetadata(0, 20, 0, 0));
        when(firmQueryService.listFirms(eq(oid), eq("Test"), eq(0), eq(20))).thenReturn(page);

        ResponseEntity<FirmViewPage> response = controller.queryFirms("Test", 0, 20, jwtWithOid(oid));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(page);
    }

    @Test
    void queryFirmById_delegatesToService() {
        controller = new FirmQueryController(firmQueryService);
        UUID oid = UUID.randomUUID();
        FirmView view = new FirmView("123456", "Test Firm", FirmType.LEGAL_SERVICES_PROVIDER, null, true);
        when(firmQueryService.getFirmById(eq(oid), eq("123456"))).thenReturn(view);

        ResponseEntity<FirmView> response = controller.queryFirmById("123456", jwtWithOid(oid));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(view);
    }

    @Test
    void queryFirmsSearch_delegatesToService() {
        controller = new FirmQueryController(firmQueryService);
        UUID oid = UUID.randomUUID();
        FirmSearchViewList list = new FirmSearchViewList(java.util.List.of());
        when(firmQueryService.searchFirms(eq(oid), eq("Test"), eq(10))).thenReturn(list);

        ResponseEntity<FirmSearchViewList> response = controller.queryFirmsSearch("Test", 10, jwtWithOid(oid));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(list);
    }

    @Test
    void queryFirmsSearch_rejectsMissingActorId() {
        controller = new FirmQueryController(firmQueryService);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test-sub")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        assertThatThrownBy(() -> controller.queryFirmsSearch("Test", 10, jwt))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessage("Authentication token is missing the actor identity");
    }

    @Test
    void queryFirmsSearch_rejectsMalformedActorId() {
        controller = new FirmQueryController(firmQueryService);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("oid", "not-a-uuid")
                .subject("test-sub")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        assertThatThrownBy(() -> controller.queryFirmsSearch("Test", 10, jwt))
                .isInstanceOf(InvalidActorContextException.class)
                .hasMessage("Authentication token contains an invalid actor identity");
    }

    @Test
    void queryFirmOffices_delegatesToService() {
        controller = new FirmQueryController(firmQueryService);
        UUID oid = UUID.randomUUID();
        OfficeViewList list = new OfficeViewList(java.util.List.of());
        when(firmQueryService.getFirmOffices(eq(oid), eq("123456"))).thenReturn(list);

        ResponseEntity<OfficeViewList> response = controller.queryFirmOffices("123456", jwtWithOid(oid));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(list);
    }
}
