package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.List;

import uk.gov.justice.laa.datauserapi.contracts.dto.FirmSearchView;

public record FirmSearchViewList(
        List<FirmSearchView> items
) {}
