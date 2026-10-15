package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;

public record FirmOfficesResponse(
        List<OfficeView> items
) {}