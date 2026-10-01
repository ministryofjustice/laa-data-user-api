package uk.gov.justice.laa.datauserapi.application.query.dto;

public record UserView(
        UserAccountSummaryView userAccount,
        UserProfileDetailView activeProfile
) {}
