package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.request.GetUserProfilesRequest;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.exception.BadRequestException;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.shared.QueryMapper;
import uk.gov.justice.laa.datauserapi.shared.MapperUtil;

import java.util.Optional;
import java.util.UUID;

import static uk.gov.justice.laa.datauserapi.shared.MapperUtil.requestFieldToEnum;

@Component
@RequiredArgsConstructor
public class GetUserProfilesQueryMapper extends QueryMapper<GetUserProfilesRequest, GetUserProfilesQuery> {

    @Override
    @SuppressWarnings("checkstyle:EmptyCatchBlock")
    public GetUserProfilesQuery map(GetUserProfilesRequest getUserProfilesRequest, AuthenticatedEntraUser authenticatedUser) {
        PageRequest pageRequest = MapperUtil.pageDataToPageRequest(getUserProfilesRequest.pageNumber(), getUserProfilesRequest.pageSize(), getUserProfilesRequest.sortBy());
        UserType userType = requestFieldToEnum(getUserProfilesRequest.userType(), UserType.class);
        UserProfileStatus userProfileStatus = requestFieldToEnum(getUserProfilesRequest.userProfileStatus(), UserProfileStatus.class);
        UUID appRoleId = MapperUtil.requestStringToUuid(getUserProfilesRequest.appRoleId());
        UUID firmId = MapperUtil.requestStringToUuid(getUserProfilesRequest.firmId());
        // "Firm" can be firm ID or firm name. If they've provided a UUID here, populate the firmId field with it, if they haven't already provided a Firm ID.
        String firm = getUserProfilesRequest.firm();
        if (firm != null) {
            try {
                UUID firmUuid = UUID.fromString(firm);
                if (firmId == null) {
                    firmId = firmUuid;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        AuthenticatedUserProfile authenticatedUserActiveProfile = Optional.ofNullable(authenticatedUser.activeProfile())
                .orElseThrow(() -> new BadRequestException("User making request does not have an active user profile or user type"));
        UserType authenticatedUserType = authenticatedUserActiveProfile.userType();
        boolean authenticatedUserCanViewExternalUser = authenticatedUserActiveProfile.appRoles().stream()
                .flatMap(appRole -> appRole.permissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);
        UUID authUserFirmId = null;
        UserType authUserType = null;
        if (authenticatedUserType == UserType.EXTERNAL) {
            authUserType = UserType.EXTERNAL;
            authUserFirmId = authenticatedUserActiveProfile.firmId();
        } else if (authenticatedUserType == UserType.INTERNAL && !authenticatedUserCanViewExternalUser) {
            authUserType = UserType.INTERNAL;
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
                pageRequest,
                authUserFirmId,
                authUserType
        );
    }
}
