package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdAppRoleReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdOfficeReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailsAppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailsOfficeView;
import uk.gov.justice.laa.datauserapi.shared.Mapper;

import java.util.List;

@Component
public class GetUserProfileByIdResponseMapper implements Mapper<GetUserProfileByIdReadModel, UserProfileDetailView> {

    @Override
    public UserProfileDetailView map(GetUserProfileByIdReadModel readModel) {
        List<UserProfileDetailsAppRoleView> roles = readModel.roles().stream()
                .map(GetUserProfileByIdResponseMapper::mapToAppRoleView)
                .toList();
        List<UserProfileDetailsOfficeView> offices = readModel.offices().stream()
                .map(GetUserProfileByIdResponseMapper::mapToOfficeView)
                .toList();
        return new UserProfileDetailView(
                readModel.userProfileId(),
                readModel.activeProfile(),
                readModel.userType(),
                readModel.email(),
                readModel.fullName(),
                readModel.firmName(),
                readModel.firmId(),
                readModel.multiFirmUser(),
                readModel.accountStatus(),
                readModel.profileStatus(),
                readModel.hasAppRoles(),
                readModel.userEntraObjectId(),
                readModel.unrestrictedOfficeAccess(),
                readModel.lastSyncSuccessful(),
                roles,
                offices
        );
    }

    private static UserProfileDetailsAppRoleView mapToAppRoleView(GetUserProfileByIdAppRoleReadModel readModel) {
        return new UserProfileDetailsAppRoleView(
                readModel.appRoleId(),
                readModel.appId(),
                readModel.appName(),
                readModel.name(),
                readModel.description(),
                readModel.authzRole(),
                readModel.ccmsCode(),
                readModel.legacySync(),
                readModel.ordinal(),
                readModel.appRoleUserTypeRestriction(),
                readModel.firmTypeRestriction()
        );
    }

    private static UserProfileDetailsOfficeView mapToOfficeView(GetUserProfileByIdOfficeReadModel readModel) {
        return new UserProfileDetailsOfficeView(
                readModel.officeId(),
                readModel.firmId(),
                readModel.postCode(),
                readModel.addressLine1(),
                readModel.addressLine2(),
                readModel.addressLine3(),
                readModel.city()
        );
    }

}
