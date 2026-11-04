package uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel;

import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.UUID;

@Builder
public record GetUserProfilesReadModel(

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
        Boolean hasAppRoles

){}
