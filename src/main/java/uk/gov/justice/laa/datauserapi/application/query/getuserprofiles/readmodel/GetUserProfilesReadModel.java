package uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel;

public record GetUserProfilesReadModel (

        String userProfileId,
        Boolean activeProfile,
        String userType,
        String email,
        String fullName,
        String firmName,
        String firmId,
        Boolean multiFirmUser,
        String accountStatus,
        String profileStatus,
        Boolean hasAppRoles

){}
