package uk.gov.justice.laa.datauserapi.dto;

import java.util.UUID;

public record AuthenticatedOffice(

        UUID id,
        String code,
        String addressLine1,
        String addressLine2,
        String addressLine3,
        String city,
        String postcode

) {}
