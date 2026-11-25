package uk.gov.justice.laa.datauserapi.contracts.dto;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.web.bind.annotation.BindParam;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.UUID;

/**
 * Filters accepted by the audit user CSV export. Unlike {@link UserSearchCriteria}, this
 * carries no paging/sorting (the export always returns every matching row) and adds an
 * {@code officeId} filter.
 *
 * <p>The export must always be constrained server-side to either a single firm or to INTERNAL
 * users only; {@link #isScopedToOneFirmOrInternalOnly()} enforces that shape and is validated via
 * {@code @Valid} on the binding, producing a 400 response for an unconstrained or conflicting
 * request before authorisation is evaluated.
 */
public record AuditUserExportCriteria(

        @BindParam("user_type")
        UserType userType,

        @BindParam("user_account_status")
        UserAccountStatus userAccountStatus,

        @BindParam("firm_id")
        String firmId,

        @BindParam("office_id")
        String officeId,

        @BindParam("app_id")
        UUID appId,

        @BindParam("app_role_id")
        UUID appRoleId,

        @BindParam("never_activated_filter")
        Boolean neverActivatedFilter
) {

    @AssertTrue(message = "Export must be constrained to a single firm (firm_id, with user_type not INTERNAL) "
            + "or to INTERNAL users only (user_type=INTERNAL with no firm_id)")
    public boolean isScopedToOneFirmOrInternalOnly() {
        boolean firmIdProvided = firmId != null && !firmId.isBlank();
        boolean internalOnly = userType == UserType.INTERNAL;
        if (internalOnly) {
            return !firmIdProvided;
        }
        return firmIdProvided;
    }
}
