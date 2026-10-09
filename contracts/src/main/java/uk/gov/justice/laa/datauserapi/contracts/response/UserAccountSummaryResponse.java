package uk.gov.justice.laa.datauserapi.contracts.response;

import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserAccountSummaryView;

import java.util.List;

public record UserAccountSummaryResponse(
        List<UserAccountSummaryView> items,
        PageMetadata page
) {}
