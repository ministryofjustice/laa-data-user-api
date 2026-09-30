package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.List;

public record FirmSearchViewList(
        List<FirmSearchView> items
) {}
