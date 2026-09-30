package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.List;

public record OfficeViewList(
        List<OfficeView> items
) {}
