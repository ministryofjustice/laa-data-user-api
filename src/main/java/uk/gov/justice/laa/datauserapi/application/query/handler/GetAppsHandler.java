package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppsQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AppCatalogueQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetAppsHandler implements QueryHandler<GetAppsQuery, AppViewList> {

    private final AppCatalogueQueryService service;

    public GetAppsHandler(AppCatalogueQueryService service) {
        this.service = service;
    }

    @Override
    public AppViewList handle(GetAppsQuery query, AuthenticatedEntraUser actor) {
        return service.getApps();
    }
}
