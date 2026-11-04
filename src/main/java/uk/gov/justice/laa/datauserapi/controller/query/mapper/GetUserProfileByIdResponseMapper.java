package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdAppRoleReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdOfficeReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailsAppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailsOfficeView;
import uk.gov.justice.laa.datauserapi.shared.ResponseMapper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class GetUserProfileByIdResponseMapper implements ResponseMapper<GetUserProfileByIdReadModel, ResponseEntity<UserProfileDetailView>> {

    @Override
    public ResponseEntity<UserProfileDetailView> map(GetUserProfileByIdReadModel readModel) {
        List<AppRoleView> roles = readModel.roles().stream()
                .map(GetUserProfileByIdResponseMapper::mapToAppRoleView)
                .toList();
        List<OfficeView> offices = readModel.offices().stream()
                .map(GetUserProfileByIdResponseMapper::mapToOfficeView)
                .toList();
        UUID userEntraObjectId = readModel.userEntraObjectId() != null ? UUID.fromString(readModel.userEntraObjectId()) : null;
        UserProfileDetailView view = new UserProfileDetailView(
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
                userEntraObjectId,
                readModel.unrestrictedOfficeAccess(),
                readModel.lastSyncSuccessful(),
                roles,
                offices
        );
        return ResponseEntity.ok(view);
    }

    private static AppRoleView mapToAppRoleView(GetUserProfileByIdAppRoleReadModel readModel) {
        return new AppRoleView(
                readModel.appRoleId().toString(),
                readModel.appId().toString(),
                readModel.appName(),
                null,
                readModel.name(),
                readModel.description(),
                readModel.authzRole(),
                readModel.ccmsCode(),
                readModel.legacySync(),
                readModel.ordinal(),
                readModel.appRoleUserTypeRestriction() == null || Arrays.stream(readModel.appRoleUserTypeRestriction()).distinct().count() != 1
                        ? "BOTH" : readModel.appRoleUserTypeRestriction()[0].name(),
                readModel.firmTypeRestriction() != null
                        ? Arrays.stream(readModel.firmTypeRestriction()).map(Enum::name).toList() : List.of()
        );
    }

    private static OfficeView mapToOfficeView(GetUserProfileByIdOfficeReadModel readModel) {
        return new OfficeView(
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
