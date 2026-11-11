package uk.gov.justice.laa.datauserapi.application.query.getuserprofiles;

import lombok.Builder;
import org.springframework.data.domain.PageRequest;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.UUID;

@Builder
public record GetUserProfilesQuery(
    UserType userType,
    UserProfileStatus userProfileStatus,
    String appId,
    UUID appRoleId,
    String officeId,
    UUID firmId,
    String user,
    String firm,
    Boolean firmAdmin,
    Boolean multiFirmUser,
    PageRequest pageRequest,
    UUID authUserFirmId,
    UserType authUserType
){}
