package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeletedUserAuditView(
        String userEntraObjectId,
        String name,
        String email,
        String deletedBy,
        LocalDateTime deletedDate,
        DeleteUserReason deleteReason,
        String deleteReasonLabel
) {}
