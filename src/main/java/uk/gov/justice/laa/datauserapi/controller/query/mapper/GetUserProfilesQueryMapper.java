package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.contracts.request.GetUserProfilesRequest;
import uk.gov.justice.laa.datauserapi.exception.BadRequestException;
import uk.gov.justice.laa.datauserapi.model.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.model.UserType;
import uk.gov.justice.laa.datauserapi.shared.Mapper;
import uk.gov.justice.laa.datauserapi.shared.MapperUtil;

import java.util.UUID;

import static uk.gov.justice.laa.datauserapi.shared.MapperUtil.requestFieldToEnum;

@Component
@RequiredArgsConstructor
public class GetUserProfilesQueryMapper implements Mapper<GetUserProfilesRequest, GetUserProfilesQuery> {

    @Override
    public GetUserProfilesQuery map(GetUserProfilesRequest getUserProfilesRequest) {
        PageRequest pageRequest = MapperUtil.pageDataToPageRequest(getUserProfilesRequest.pageNumber(), getUserProfilesRequest.pageSize(), getUserProfilesRequest.sortBy());
        UserType userType = requestFieldToEnum(getUserProfilesRequest.userType(), UserType.class);
        UserProfileStatus userProfileStatus = requestFieldToEnum(getUserProfilesRequest.userProfileStatus(), UserProfileStatus.class);
        UUID appRoleId = MapperUtil.requestStringToUUID(getUserProfilesRequest.appRoleId());
        UUID firmId = MapperUtil.requestStringToUUID(getUserProfilesRequest.firmId());
        // "Firm" can be firm ID or firm name. If they've provided a UUID here, populate the firmId field with it, if they haven't already provided a Firm ID.
        String firm = getUserProfilesRequest.firm();
        if(firm != null) {
            try {
                UUID firmUuid = UUID.fromString(firm);
                if(firmId == null) {
                    firmId = firmUuid;
                }
            } catch (IllegalArgumentException ignored){}
        }
        return new GetUserProfilesQuery(
                userType,
                userProfileStatus,
                getUserProfilesRequest.appId(),
                appRoleId,
                getUserProfilesRequest.officeId(),
                firmId,
                getUserProfilesRequest.user(),
                getUserProfilesRequest.firm(),
                getUserProfilesRequest.firmAdmin(),
                getUserProfilesRequest.multiFirmUser(),
                pageRequest
        );
    }

    private static UserType mapUserType(GetUserProfilesRequest getUserProfilesRequest) {
        UserType userType;
        try {
            userType = UserType.valueOf(getUserProfilesRequest.userType());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Provided user type \"%s\" is not a valid user type", getUserProfilesRequest.userType());
        }
        return userType;
    }


}
