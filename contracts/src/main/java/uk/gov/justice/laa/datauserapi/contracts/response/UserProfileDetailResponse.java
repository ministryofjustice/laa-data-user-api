package uk.gov.justice.laa.datauserapi.contracts.response;

import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileDetailView;

public record UserProfileDetailResponse (
       UserProfileDetailView userProfileDetailView
) {}
