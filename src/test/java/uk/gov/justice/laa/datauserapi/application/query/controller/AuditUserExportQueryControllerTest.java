package uk.gov.justice.laa.datauserapi.application.query.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.handler.ExportAuditUsersHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.service.csv.CsvExportWriter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditUserExportQueryControllerTest {

    @Mock
    private ExportAuditUsersHandler handler;

    private final CsvExportWriter csvExportWriter = new CsvExportWriter();

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2024-03-15T14:32:00Z"), ZoneOffset.UTC);

    @Test
    void exportAuditUsers_firmScoped_setsCsvContentTypeAndFirmFilename() throws Exception {
        AuditUserExportQueryController controller =
                new AuditUserExportQueryController(handler, csvExportWriter, FIXED_CLOCK);
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.EXTERNAL, null, "FIRM001", null, null, null, null);
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid("actor-oid").build();
        when(handler.handle(any(ExportAuditUsersQuery.class), any(AuthenticatedEntraUser.class)))
                .thenReturn(List.of());

        ResponseEntity<StreamingResponseBody> response = controller.exportAuditUsers(criteria, actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("user-access-audit_FIRM001_2024-03-15_1432_UTC.csv");
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("text/csv");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toString(StandardCharsets.UTF_8)).startsWith("Name,Email");
    }

    @Test
    void exportAuditUsers_internalOnly_usesInternalFilenameSegment() {
        AuditUserExportQueryController controller =
                new AuditUserExportQueryController(handler, csvExportWriter, FIXED_CLOCK);
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid("actor-oid").build();
        when(handler.handle(any(ExportAuditUsersQuery.class), any(AuthenticatedEntraUser.class)))
                .thenReturn(List.of());

        ResponseEntity<StreamingResponseBody> response = controller.exportAuditUsers(criteria, actor);

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("user-access-audit_INTERNAL_2024-03-15_1432_UTC.csv");
    }

    @Test
    void exportAuditUsers_passesCriteriaThroughQuery() {
        AuditUserExportQueryController controller =
                new AuditUserExportQueryController(handler, csvExportWriter, FIXED_CLOCK);
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid("actor-oid").build();
        when(handler.handle(any(ExportAuditUsersQuery.class), any(AuthenticatedEntraUser.class)))
                .thenReturn(List.of());

        controller.exportAuditUsers(criteria, actor);

        verify(handler).handle(new ExportAuditUsersQuery(criteria), actor);
    }
}
