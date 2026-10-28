package uk.gov.justice.laa.datauserapi.contracts.dto;


import java.util.List;

public record AppRoleView(
        String appRoleId,
        String appId,
        String appName,
        String appEntraClientId,
        String name,
        String description,
        boolean authzRole,
        String roleIdentifier,
        boolean legacySync,
        Integer ordinal,
        String appRoleUserTypeRestriction,
        List<String> firmTypeRestriction
) {}
