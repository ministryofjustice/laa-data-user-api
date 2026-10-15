package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.GetUserProfileByIdQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.exception.BadRequestException;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.shared.QueryMapper;
import uk.gov.justice.laa.datauserapi.shared.MapperUtil;

import java.util.Optional;
import java.util.UUID;

@Component
public class GetUserProfileByIdQueryMapper extends QueryMapper<String, GetUserProfileByIdQuery> {

    @Override
    public GetUserProfileByIdQuery map(String id, AuthenticatedEntraUser authenticatedUser) {
        UUID uuid = MapperUtil.requestStringToUuid(id);
        AuthenticatedUserProfile authenticatedUserActiveProfile = Optional.ofNullable(authenticatedUser.activeProfile())
                .orElseThrow(() -> new BadRequestException("User making request does not have an active user profile or user type"));
        UserType authenticatedUserType = authenticatedUserActiveProfile.userType();
        boolean authenticatedUserCanViewExternalUser = authenticatedUserActiveProfile.appRoles().stream()
                .flatMap(appRole -> appRole.permissions().stream())
                .anyMatch(permission -> permission == Permission.VIEW_EXTERNAL_USER);
        UUID firmId = null;
        UserType queriedUserType = null;
        if (authenticatedUserType == UserType.EXTERNAL) {
            queriedUserType = UserType.EXTERNAL;
            firmId = authenticatedUserActiveProfile.firmId();
        } else if (authenticatedUserType == UserType.INTERNAL && !authenticatedUserCanViewExternalUser) {
            queriedUserType = UserType.INTERNAL;
        }
        return new GetUserProfileByIdQuery(uuid, firmId, queriedUserType);
    }
}
