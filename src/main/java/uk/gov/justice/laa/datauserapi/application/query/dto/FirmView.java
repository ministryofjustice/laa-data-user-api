package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.model.FirmType;

public record FirmView(
        String firmId,
        String name,
        FirmType type,
        String parentFirmId,
        boolean enabled
) {}
