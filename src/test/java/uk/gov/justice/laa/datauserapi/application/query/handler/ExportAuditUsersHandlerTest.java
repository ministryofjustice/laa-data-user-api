package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.dto.ExportBody;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserExportService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.io.ByteArrayOutputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExportAuditUsersHandlerTest {

    @Test
    void handle_returnsExportBodyThatDelegatesToService() throws Exception {
        AuditUserExportService service = mock(AuditUserExportService.class);
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);
        ExportAuditUsersQuery query = new ExportAuditUsersQuery(criteria);
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);

        ExportBody body = new ExportAuditUsersHandler(service).handle(query, actor);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);

        verify(service).export(criteria, out);
    }
}

