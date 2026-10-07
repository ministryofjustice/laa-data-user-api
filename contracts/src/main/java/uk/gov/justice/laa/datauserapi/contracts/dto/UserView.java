package uk.gov.justice.laa.datauserapi.contracts.dto;

public record UserView(
        UserAccountSummaryView userAccount,
        UserProfileDetailView activeProfile
) {}
