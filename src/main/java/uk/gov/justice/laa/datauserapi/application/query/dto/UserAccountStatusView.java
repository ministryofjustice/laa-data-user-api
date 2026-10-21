package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.model.UserAccountStatus;

import java.time.LocalDateTime;

public record UserAccountStatusView(
        String userEntraObjectId,
        String email,
        UserAccountStatus accountStatus,
        LocalDateTime lastModifiedDate
) {}
