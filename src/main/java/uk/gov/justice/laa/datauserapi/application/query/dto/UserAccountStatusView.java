package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.model.SilasAccountStatus;

import java.time.LocalDateTime;

public record UserAccountStatusView(
        String userEntraObjectId,
        String email,
        SilasAccountStatus accountStatus,
        LocalDateTime lastModifiedDate
) {}
