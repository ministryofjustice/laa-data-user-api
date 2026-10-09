package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

import uk.gov.justice.laa.datauserapi.contracts.dto.FirmSearchView;

public record FirmSearchResponse(
        List<FirmSearchView> items
) {}