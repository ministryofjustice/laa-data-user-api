package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccountStatusHistoryView(
        UUID entraUserId,
        String userEntraObjectId,
        UserAccountStatus userAccountStatus,
        LocalDateTime statusChangedDate,
        String statusChangedBy,
        String deactivateReason
) {}
