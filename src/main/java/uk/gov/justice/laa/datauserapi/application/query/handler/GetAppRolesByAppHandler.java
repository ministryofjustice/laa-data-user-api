package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppRolesByAppQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AppCatalogueQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetAppRolesByAppHandler implements QueryHandler<GetAppRolesByAppQuery, AppRoleViewList> {

    private final AppCatalogueQueryService service;

    public GetAppRolesByAppHandler(AppCatalogueQueryService service) {
        this.service = service;
    }

    @Override
    public AppRoleViewList handle(GetAppRolesByAppQuery query, AuthenticatedEntraUser actor) {
        return service.getAppRoles(query.appId(), query.userType());
    }
}
