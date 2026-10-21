package uk.gov.justice.laa.datauserapi.contracts.dto;

import java.util.List;

public record UserAccountSummaryPage(
        List<UserAccountSummaryView> items,
        PageMetadata page
) {}
