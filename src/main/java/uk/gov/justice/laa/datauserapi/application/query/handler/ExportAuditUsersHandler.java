package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.ExportBody;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserExportService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class ExportAuditUsersHandler implements QueryHandler<ExportAuditUsersQuery, ExportBody> {

    private final AuditUserExportService service;

    public ExportAuditUsersHandler(AuditUserExportService service) {
        this.service = service;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public ExportBody handle(ExportAuditUsersQuery query, AuthenticatedEntraUser actor) {
        return outputStream -> service.export(query.criteria(), outputStream);
    }
}

