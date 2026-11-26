package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppRoleByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AppCatalogueQueryService;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetAppRoleByIdHandler implements QueryHandler<GetAppRoleByIdQuery, AppRoleView> {

    private final AppCatalogueQueryService service;

    public GetAppRoleByIdHandler(AppCatalogueQueryService service) {
        this.service = service;
    }

    @Override
    public AppRoleView handle(GetAppRoleByIdQuery query, AuthenticatedEntraUser actor) {
        return service.getAppRole(query.appRoleId());
    }
}
