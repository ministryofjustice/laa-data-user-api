package uk.gov.justice.laa.datauserapi.application.query.dto;

public record OfficeView(
        String officeId,
        String firmId,
        String postCode,
        String addressLine1,
        String addressLine2,
        String addressLine3,
        String city
) {}
