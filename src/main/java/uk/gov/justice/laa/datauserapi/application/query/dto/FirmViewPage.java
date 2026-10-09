package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.List;

import uk.gov.justice.laa.datauserapi.contracts.dto.FirmView;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;

public record FirmViewPage(
        List<FirmView> items,
        PageMetadata page
) {}
