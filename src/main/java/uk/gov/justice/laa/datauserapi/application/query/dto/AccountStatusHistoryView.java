package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccountStatusHistoryView(
        UUID entraUserId,
        String userEntraObjectId,
        UserAccountAuditStatus userAccountAuditStatus,
        LocalDateTime statusChangedDate,
        String statusChangedBy,
        String deactivateReason
) {}
