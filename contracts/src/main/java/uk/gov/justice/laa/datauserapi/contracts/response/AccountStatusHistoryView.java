package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountStatusHistoryView(
        String userEntraObjectId,
        UserAccountAuditStatus statusChange,
        LocalDateTime statusChangedDate,
        String statusChangedBy,
        DeactivateUserReason deactivateReason,
        String deactivateReasonLabel
) {}
