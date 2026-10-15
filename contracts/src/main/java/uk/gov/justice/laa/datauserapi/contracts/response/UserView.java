package uk.gov.justice.laa.datauserapi.contracts.response;

public record UserView(
        UserAccountSummaryView userAccount,
        UserProfileDetailView activeProfile
) {}
