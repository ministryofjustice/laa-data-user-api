package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsVisibilityPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetActiveProfileHandler implements QueryHandler<GetActiveProfileQuery, UserProfileDetailView> {

    private final UserProfileDetailsService service;
    private final UserProfileDetailsVisibilityPolicyInputResolver resolver;

    public GetActiveProfileHandler(UserProfileDetailsService service,
                                   UserProfileDetailsVisibilityPolicyInputResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @Override
    public UserProfileDetailView handle(GetActiveProfileQuery query, AuthenticatedEntraUser actor) {
        ProfileVisibilityContext authorizedContext = resolver.resolve(query);
        return service.getActiveProfile(query.userEntraObjectId(), authorizedContext.profileId());
    }
}
