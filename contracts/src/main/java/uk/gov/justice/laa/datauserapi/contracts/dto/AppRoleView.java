package uk.gov.justice.laa.datauserapi.contracts.dto;


import java.util.List;
import java.util.UUID;

public record AppRoleView(
        UUID appRoleId,
        UUID appId,
        String appName,
        String name,
        String description,
        boolean authzRole,
        String ccmsCode,
        boolean legacySync,
        Integer ordinal,
        String appRoleUserTypeRestriction,
        List<String> firmTypeRestriction
) {}
