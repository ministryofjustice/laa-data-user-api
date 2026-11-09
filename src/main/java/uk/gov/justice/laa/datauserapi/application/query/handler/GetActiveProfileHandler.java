package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryprofile.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.GetActiveProfileQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationEntryPoint;

@Component
@Transactional(readOnly = true)
public class GetActiveProfileHandler implements AuthorizationEntryPoint {

    private final GetActiveProfileQueryService getActiveProfileQueryService;

    public GetActiveProfileHandler(GetActiveProfileQueryService getActiveProfileQueryService,
                                    ModelMapper modelMapper) {
        this.getActiveProfileQueryService = getActiveProfileQueryService;
    }

    public UserProfileDetailView handle(GetActiveProfileQuery query) {
        return getActiveProfileQueryService.getUserActiveProfile(query.userEntraObjectId());
    }
}


