package uk.gov.justice.laa.datauserapi.contracts.response;

import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;

import java.time.LocalDateTime;

public record AccountStatusHistoryView(
        String userEntraObjectId,
        UserAccountAuditStatus statusChange,
        LocalDateTime statusChangedDate,
        String statusChangedBy,
        DeactivateUserReason deactivateReason,
        String deactivateReasonLabel
) {}
