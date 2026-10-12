package uk.gov.justice.laa.datauserapi.contracts.response;

import uk.gov.justice.laa.datauserapi.contracts.dto.UserAccountSummaryView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileDetailView;

public record UserViewResponse (
        UserAccountSummaryView userAccount,
        UserProfileDetailView activeProfile
){}
