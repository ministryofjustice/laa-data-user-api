package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.List;

public record FirmViewPage(
        List<FirmView> items,
        PageMetadata page
) {}
