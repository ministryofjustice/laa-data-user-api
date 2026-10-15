package uk.gov.justice.laa.datauserapi.dto;

import uk.gov.justice.laa.datauserapi.model.AppType;

import java.util.UUID;

public record AuthenticatedApp(

        UUID id,
        String name,
        String description,
        AppType appType,
        int ordinal,
        String url,
        boolean enabled,
        String entraAppId,
        String entraOid,
        String securityGroupOid

){}
