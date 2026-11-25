package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserExportService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportAuditUsersHandlerTest {

    @Test
    void delegatesQueryAndPreservesResult() {
        AuditUserExportService service = mock(AuditUserExportService.class);
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);
        ExportAuditUsersQuery query = new ExportAuditUsersQuery(criteria);
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        List<AuditUserExportRow> expected = List.of(mock(AuditUserExportRow.class));
        when(service.export(query)).thenReturn(expected);

        assertThat(new ExportAuditUsersHandler(service).handle(query, actor)).isSameAs(expected);
        verify(service).export(query);
    }
}
