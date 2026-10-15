package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel;

public record GetUserProfileByIdAppRoleReadModel(
        
        String appRoleId,
        String appId,
        String appName,
        String name,
        String description,
        Boolean authzRole,
        String ccmsCode,
        Boolean legacySync,
        Integer ordinal,
        String[] appRoleUserTypeRestriction,
        String[] firmTypeRestriction
        
) {}
