package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdAppRoleReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdOfficeReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.shared.MapperUtil;
import uk.gov.justice.laa.datauserapi.shared.ReadMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class GetUserProfileByIdReadMapper implements ReadMapper<UserProfile, GetUserProfileByIdReadModel> {

    @Override
    public GetUserProfileByIdReadModel map(UserProfile userProfile) {
        List<GetUserProfileByIdAppRoleReadModel> appRoleReadModels = userProfile.getAppRoles().stream()
                .map(GetUserProfileByIdReadMapper::mapAppRoleToReadModel)
                .toList();
        List<GetUserProfileByIdOfficeReadModel> officeReadModels = userProfile.getOffices().stream()
                .map(GetUserProfileByIdReadMapper::mapOfficeToReadModel)
                .toList();
        return mapToGetUserProfileByIdReadModel(userProfile, appRoleReadModels, officeReadModels);
    }

    private static GetUserProfileByIdReadModel mapToGetUserProfileByIdReadModel(UserProfile userProfile,
                                                                                List<GetUserProfileByIdAppRoleReadModel> appRoleReadModels,
                                                                                List<GetUserProfileByIdOfficeReadModel> officeReadModels) {
        Optional<EntraUser> entraUser = Optional.ofNullable(userProfile.getEntraUser());
        String email = entraUser.map(EntraUser::getEmail).orElse(null);
        String firstName = entraUser.map(EntraUser::getFirstName).orElse(null);
        String lastName = entraUser.map(EntraUser::getLastName).orElse(null);
        String fullName = MapperUtil.buildFullName(firstName, lastName);
        Optional<Firm> firm = Optional.ofNullable(userProfile.getFirm());
        String firmName = firm.map(Firm::getName).orElse(null);
        String firmId = firm.map(Firm::getId).map(UUID::toString).orElse(null);
        Boolean multiFirmUser = entraUser.map(EntraUser::isMultiFirmUser).orElse(null);
        String accountStatus = entraUser.map(EntraUser::getUserAccountStatus).map(UserAccountStatus::name).orElse(null);
        String profileStatus = Optional.ofNullable(userProfile.getUserProfileStatus()).map(UserProfileStatus::toString).orElse(null);
        Boolean hasAppRoles = userProfile.getAppRoles() != null && !userProfile.getAppRoles().isEmpty();
        String userEntraObjectId = entraUser.map(EntraUser::getEntraOid).orElse(null);
        Boolean unrestrictedOfficeAccess = userProfile.isUnrestrictedOfficeAccess();
        Boolean lastSyncSuccessful = userProfile.isLastCcmsSyncSuccessful();
        return new GetUserProfileByIdReadModel(
                userProfile.getId().toString(),
                userProfile.isActiveProfile(),
                userProfile.getUserType().name(),
                email,
                fullName,
                firmName,
                firmId,
                multiFirmUser,
                accountStatus,
                profileStatus,
                hasAppRoles,
                userEntraObjectId,
                unrestrictedOfficeAccess,
                lastSyncSuccessful,
                appRoleReadModels,
                officeReadModels
        );
    }

    private static GetUserProfileByIdAppRoleReadModel mapAppRoleToReadModel(AppRole appRole) {
        Optional<App> app = Optional.of(appRole.getApp());
        String appId = app.map(App::getId).map(UUID::toString).orElse(null);
        String appName = app.map(App::getName).orElse(null);
        String[] userTypeRestrictions = null;
        if (appRole.getUserTypeRestriction() != null) {
            userTypeRestrictions = Arrays.stream(appRole.getUserTypeRestriction())
                    .map(Enum::name)
                    .toArray(String[]::new);
        }
        String[] firmTypeRestrictions = null;
        if (appRole.getFirmTypeRestriction() != null) {
            firmTypeRestrictions = Arrays.stream(appRole.getFirmTypeRestriction())
                    .map(Enum::name)
                    .toArray(String[]::new);
        }
        return new GetUserProfileByIdAppRoleReadModel(
                appRole.getId().toString(),
                appId,
                appName,
                appRole.getName(),
                appRole.getDescription(),
                appRole.isAuthzRole(),
                appRole.getCcmsCode(),
                appRole.isLegacySync(),
                appRole.getOrdinal(),
                userTypeRestrictions,
                firmTypeRestrictions
        );
    }

    private static GetUserProfileByIdOfficeReadModel mapOfficeToReadModel(Office office) {
        Optional<Firm> firm = Optional.ofNullable(office.getFirm());
        String firmId = firm.map(Firm::getId).map(UUID::toString).orElse(null);
        Optional<Office.Address> address = Optional.ofNullable(office.getAddress());
        String postCode = address.map(Office.Address::getPostcode).orElse(null);
        String addressLine1 = address.map(Office.Address::getAddressLine1).orElse(null);
        String addressLine2 = address.map(Office.Address::getAddressLine2).orElse(null);
        String addressLine3 = address.map(Office.Address::getAddressLine3).orElse(null);
        String city = address.map(Office.Address::getCity).orElse(null);
        return new GetUserProfileByIdOfficeReadModel(
                office.getId().toString(),
                firmId,
                postCode,
                addressLine1,
                addressLine2,
                addressLine3,
                city
        );
    }
}
