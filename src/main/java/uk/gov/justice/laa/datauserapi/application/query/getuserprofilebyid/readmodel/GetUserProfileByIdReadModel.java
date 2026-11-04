package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel;

import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.Collection;
import java.util.UUID;

@Builder
public record GetUserProfileByIdReadModel(

        UUID userProfileId,
        Boolean activeProfile,
        UserType userType,
        String email,
        String fullName,
        String firmName,
        String firmId,
        Boolean multiFirmUser,
        UserAccountStatus accountStatus,
        UserProfileStatus profileStatus,
        Boolean hasAppRoles,
        String userEntraObjectId,
        Boolean unrestrictedOfficeAccess,
        Boolean lastSyncSuccessful,
        Collection<GetUserProfileByIdAppRoleReadModel> roles,
        Collection<GetUserProfileByIdOfficeReadModel> offices

){}
