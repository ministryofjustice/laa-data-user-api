package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AuditUserDetailService;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class AuditUserDetailHandler implements QueryHandler<AuditUserDetailQuery, AuditUserDetailView> {

    private final AuditUserDetailService service;

    public AuditUserDetailHandler(AuditUserDetailService service) {
        this.service = service;
    }

    @Override
    public AuditUserDetailView handle(AuditUserDetailQuery query, AuthenticatedEntraUser actor) {
        return service.getDetail(query, actor);
    }
}
