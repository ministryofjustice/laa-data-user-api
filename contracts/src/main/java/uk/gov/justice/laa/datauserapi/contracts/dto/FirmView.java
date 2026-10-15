package uk.gov.justice.laa.datauserapi.contracts.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;

public record FirmView(
        String firmId,
        String name,
        FirmType type,
        String parentFirmId,
        boolean enabled
) {}