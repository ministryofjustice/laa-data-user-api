package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

/**
 * Trusted facts needed to authorise an audit user export request: the requested scope,
 * extracted from the query's criteria.
 */
public record AuditUserExportAuthorizationContext(
        UserType requestedUserType,
        String requestedFirmId,
        boolean scopedToOneFirmOrInternalOnly
) {

    public boolean requestsInternalOnly() {
        return requestedUserType == UserType.INTERNAL;
    }
}
