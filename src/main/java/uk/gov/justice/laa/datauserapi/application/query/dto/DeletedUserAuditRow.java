package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.time.LocalDateTime;

/** Typed projection of the deletion snapshot stored in {@code user_account_status_audit}. */
public record DeletedUserAuditRow(
        String userEntraObjectId,
        String name,
        String email,
        String deletedBy,
        LocalDateTime deletedDate,
        String deleteReasonCode,
        String deleteReasonLabel
) {}
