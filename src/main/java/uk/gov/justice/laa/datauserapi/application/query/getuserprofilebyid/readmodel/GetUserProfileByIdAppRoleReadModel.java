package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel;

import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.UUID;

public record GetUserProfileByIdAppRoleReadModel(
        
        UUID appRoleId,
        UUID appId,
        String appName,
        String name,
        String description,
        Boolean authzRole,
        String ccmsCode,
        Boolean legacySync,
        Integer ordinal,
        UserType[] appRoleUserTypeRestriction,
        FirmType[] firmTypeRestriction
        
) {}
