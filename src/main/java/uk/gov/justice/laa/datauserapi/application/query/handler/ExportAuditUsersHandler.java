package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserExportService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class ExportAuditUsersHandler implements QueryHandler<ExportAuditUsersQuery, List<AuditUserExportRow>> {

    private final AuditUserExportService service;

    public ExportAuditUsersHandler(AuditUserExportService service) {
        this.service = service;
    }

    @Override
    public List<AuditUserExportRow> handle(ExportAuditUsersQuery query, AuthenticatedEntraUser actor) {
        return service.export(query);
    }
}
