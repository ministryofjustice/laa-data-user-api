package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetRoleAssignmentRestrictionsQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.RoleAssignmentRestrictionQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.RoleAssignmentRestrictionViewPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetRoleAssignmentRestrictionsHandler
        implements QueryHandler<GetRoleAssignmentRestrictionsQuery, RoleAssignmentRestrictionViewPage> {

    private final RoleAssignmentRestrictionQueryService service;

    public GetRoleAssignmentRestrictionsHandler(RoleAssignmentRestrictionQueryService service) {
        this.service = service;
    }

    @Override
    public RoleAssignmentRestrictionViewPage handle(GetRoleAssignmentRestrictionsQuery query,
                                                    AuthenticatedEntraUser actor) {
        return service.getRestrictions(query.appId(), query.pageNumber(), query.pageSize());
    }
}
