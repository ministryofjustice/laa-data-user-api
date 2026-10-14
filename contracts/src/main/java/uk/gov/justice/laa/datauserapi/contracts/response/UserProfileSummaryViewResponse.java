package uk.gov.justice.laa.datauserapi.contracts.response;

import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileSummaryView;

import java.util.List;

public record UserProfileSummaryViewResponse(
        List<UserProfileSummaryView> userProfileSummaryView
) {}
