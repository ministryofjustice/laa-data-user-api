package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

public record UserProfileSummaryList(
        List<UserProfileSummaryView> items
) {}
