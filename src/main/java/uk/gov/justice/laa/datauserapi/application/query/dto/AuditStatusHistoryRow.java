package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;

import java.time.LocalDateTime;

/** Typed projection of a {@code user_account_status_audit} row joined to its deactivation reason. */
public record AuditStatusHistoryRow(
        String userEntraObjectId,
        UserAccountAuditStatus statusChange,
        LocalDateTime statusChangedDate,
        String statusChangedBy,
        String deactivateReasonName,
        String deactivateReasonLabel
) {}
