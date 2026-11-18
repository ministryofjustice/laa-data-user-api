package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.contracts.response.CurrentUserPermissionsResponse;
import uk.gov.justice.laa.datauserapi.security.opa.OpaAuthorizationClient;

@Service
@Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
public class CurrentUserPermissionsService {

    private final OpaAuthorizationClient client;

    public CurrentUserPermissionsService(OpaAuthorizationClient client) {
        this.client = client;
    }

    public CurrentUserPermissionsResponse permissions(List<String> roles) {
        return client.callerPermissions(roles)
                .map(CurrentUserPermissionsResponse::new)
                .orElseThrow(() -> new AccessDeniedException("OPA denied caller permission resolution"));
    }
}
