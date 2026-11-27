package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEntraUserView(
        String userEntraObjectId,
        String email,
        String firstName,
        String lastName,
        UserAccountStatus accountStatus,
        Boolean multiFirmUser,
        Boolean mailOnly,
        LocalDateTime lastSyncedOn,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate,
        Integer profileCount,
        String entraInviteStatus,
        LocalDateTime lastLoginDate
) {}
