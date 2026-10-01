package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.List;

public record UserAccountSummaryPage(
        List<UserAccountSummaryView> items,
        PageMetadata page
) {}
