package uk.gov.justice.laa.datauserapi.application.query.dto;

public record FirmSearchAccess(
        boolean allFirms,
        String firmCode
) {}
