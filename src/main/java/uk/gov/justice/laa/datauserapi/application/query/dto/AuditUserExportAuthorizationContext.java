package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

/**
 * Trusted facts needed to authorise an audit user export request: the requested scope,
 * extracted from the query's criteria.
 *
 * <p>{@code scopedToOneFirmOrInternalOnly} restates
 * {@code AuditUserExportCriteria#isScopedToOneFirmOrInternalOnly()} as a trusted fact so the
 * policy can fail closed even when a caller invokes the Spring-proxied handler directly,
 * bypassing the controller's {@code @Valid} request-shape validation.
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
