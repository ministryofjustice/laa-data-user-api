package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.DeletedUsersAuditQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.DeletedUsersAuditService;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class DeletedUsersAuditHandler implements QueryHandler<DeletedUsersAuditQuery, DeletedUserAuditPage> {

    private final DeletedUsersAuditService service;

    public DeletedUsersAuditHandler(DeletedUsersAuditService service) {
        this.service = service;
    }

    @Override
    public DeletedUserAuditPage handle(DeletedUsersAuditQuery query, AuthenticatedEntraUser actor) {
        return service.getDeletedUsers(query, actor);
    }
}
