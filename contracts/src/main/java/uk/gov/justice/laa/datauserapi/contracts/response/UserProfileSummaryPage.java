package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;

public record UserProfileSummaryPage(List<UserProfileSummaryView> items, PageMetadata page) {
}
