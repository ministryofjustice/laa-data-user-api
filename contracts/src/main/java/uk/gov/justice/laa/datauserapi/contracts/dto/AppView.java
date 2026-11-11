package uk.gov.justice.laa.datauserapi.contracts.dto;

public record AppView(
        String appId,
        String name,
        String description,
        String url,
        boolean active,
        int ordinal,
        String appEntraObjectId,
        String appEntraClientId
) {}
