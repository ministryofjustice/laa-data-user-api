package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel;

import lombok.Builder;

import java.util.Collection;

@Builder
public record GetUserProfileByIdReadModel(

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
        Boolean hasAppRoles,
        String userEntraObjectId,
        Boolean unrestrictedOfficeAccess,
        Boolean lastSyncSuccessful,
        Collection<GetUserProfileByIdAppRoleReadModel> roles,
        Collection<GetUserProfileByIdOfficeReadModel> offices

){}
