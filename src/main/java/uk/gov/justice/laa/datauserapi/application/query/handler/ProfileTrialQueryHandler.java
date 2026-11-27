package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.profiletrial.ProfileTrialQuery;
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileTrialQueryResult;
import uk.gov.justice.laa.datauserapi.application.query.service.ProfileTrialQueryService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationEntryPoint;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialOperation;
import uk.gov.justice.laa.datauserapi.security.rbac.InvalidAuthorizationConfigurationException;

@Component
@Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
public class ProfileTrialQueryHandler implements AuthorizationEntryPoint {
    private final ProfileTrialQueryService service;

    public ProfileTrialQueryHandler(ProfileTrialQueryService service) {
        this.service = service;
    }

    public ProfileTrialQueryResult handle(ProfileTrialQuery query, AuthenticatedEntraUser actor) {
        if (query.request().operation() == ProfileTrialOperation.SEARCH) {
            return new ProfileTrialQueryResult(service.search(query), null, null);
        }
        if (query.request().operation() == ProfileTrialOperation.DETAIL) {
            return new ProfileTrialQueryResult(null, service.detail(query), null);
        }
        if (query.request().operation() == ProfileTrialOperation.ASSIGNABLE) {
            return new ProfileTrialQueryResult(null, null, service.assignable(query));
        }
        throw new InvalidAuthorizationConfigurationException(
                "A mutation was routed to the profile query handler");
    }
}
