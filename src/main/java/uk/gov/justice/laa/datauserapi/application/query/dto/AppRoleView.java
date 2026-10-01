package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.model.AppRoleUserType;
import uk.gov.justice.laa.datauserapi.model.FirmType;

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
        AppRoleUserType appRoleUserTypeRestriction,
        List<FirmType> firmTypeRestriction
) {}
