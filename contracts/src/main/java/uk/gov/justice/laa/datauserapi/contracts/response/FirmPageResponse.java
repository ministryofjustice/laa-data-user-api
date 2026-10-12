package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

import uk.gov.justice.laa.datauserapi.contracts.dto.FirmView;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;

public record FirmPageResponse(
        List<FirmView> items,
        PageMetadata page
) {}