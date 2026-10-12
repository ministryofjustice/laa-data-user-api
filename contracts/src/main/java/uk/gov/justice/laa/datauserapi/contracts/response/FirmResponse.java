package uk.gov.justice.laa.datauserapi.contracts.response;

import uk.gov.justice.laa.datauserapi.contracts.dto.FirmView;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;

public record FirmResponse(
        String firmId,
        String name,
        FirmType type,
        String parentFirmId,
        boolean enabled
) {

    public FirmResponse(FirmView firm) {
        this(firm.firmId(), firm.name(), firm.type(), firm.parentFirmId(), firm.enabled());
    }
}