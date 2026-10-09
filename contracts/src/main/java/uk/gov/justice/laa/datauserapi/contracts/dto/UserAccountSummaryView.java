package uk.gov.justice.laa.datauserapi.contracts.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;

import java.time.LocalDateTime;

public record UserAccountSummaryView(
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
        Integer profileCount
) {}
