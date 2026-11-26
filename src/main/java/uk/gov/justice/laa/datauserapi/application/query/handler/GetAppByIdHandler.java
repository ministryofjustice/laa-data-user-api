package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.AppCatalogueQueryService;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetAppByIdHandler implements QueryHandler<GetAppByIdQuery, AppView> {

    private final AppCatalogueQueryService service;

    public GetAppByIdHandler(AppCatalogueQueryService service) {
        this.service = service;
    }

    @Override
    public AppView handle(GetAppByIdQuery query, AuthenticatedEntraUser actor) {
        return service.getApp(query.appId());
    }
}
