package uk.gov.justice.laa.datauserapi.contracts.response;

import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;

import java.time.LocalDateTime;

public record DeletedUserAuditView(
        String userEntraObjectId,
        String name,
        String email,
        String deletedBy,
        LocalDateTime deletedDate,
        DeleteUserReason deleteReason,
        String deleteReasonLabel
) {}
