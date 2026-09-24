package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;

import java.time.LocalDateTime;

public record AccountStatusHistoryView(
        String userEntraObjectId,
        UserAccountStatus userAccountStatus,
        LocalDateTime statusChangedDate,
        String statusChangedBy,
        String disableReason
) {}
