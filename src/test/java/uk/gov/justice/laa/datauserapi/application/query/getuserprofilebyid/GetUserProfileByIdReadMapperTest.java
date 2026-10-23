package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdAppRoleReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdOfficeReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.util.TestObjectUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GetUserProfileByIdReadMapperTest {

    private GetUserProfileByIdReadMapper readMapper;

    @BeforeEach
    public void setUp() {
        readMapper = new GetUserProfileByIdReadMapper();
    }

    @Test
    public void testMapShouldCorrectlyMapAllFieldsIncludingNestedRolesAndOffices() {
        // Given
        UserProfile userProfile = buildRichUserProfile();

        // When
        GetUserProfileByIdReadModel readModel = readMapper.map(userProfile);

        // Then
        List<GetUserProfileByIdAppRoleReadModel> roles = List.copyOf(readModel.roles());
        List<GetUserProfileByIdOfficeReadModel> offices = List.copyOf(readModel.offices());

        Assertions.assertAll(
                () -> Assertions.assertEquals(userProfile.getId().toString(), readModel.userProfileId()),
                () -> Assertions.assertEquals(userProfile.isActiveProfile(), readModel.activeProfile()),
                () -> Assertions.assertEquals(userProfile.getUserType().name(), readModel.userType()),
                () -> Assertions.assertEquals(userProfile.getEntraUser().getEmail(), readModel.email()),
                () -> Assertions.assertEquals(userProfile.getEntraUser().getFirstName() + " " + userProfile.getEntraUser().getLastName(), readModel.fullName()),
                () -> Assertions.assertEquals(userProfile.getFirm().getName(), readModel.firmName()),
                () -> Assertions.assertEquals(userProfile.getFirm().getId().toString(), readModel.firmId()),
                () -> Assertions.assertEquals(userProfile.getEntraUser().isMultiFirmUser(), readModel.multiFirmUser()),
                () -> Assertions.assertEquals(userProfile.getEntraUser().getUserAccountStatus().name(), readModel.accountStatus()),
                () -> Assertions.assertEquals(userProfile.getUserProfileStatus().toString(), readModel.profileStatus()),
                () -> Assertions.assertTrue(readModel.hasAppRoles()),
                () -> Assertions.assertEquals(userProfile.getEntraUser().getEntraOid(), readModel.userEntraObjectId()),
                () -> Assertions.assertEquals(userProfile.isUnrestrictedOfficeAccess(), readModel.unrestrictedOfficeAccess()),
                () -> Assertions.assertEquals(userProfile.isLastCcmsSyncSuccessful(), readModel.lastSyncSuccessful()),
                () -> Assertions.assertEquals(2, roles.size()),
                () -> Assertions.assertEquals(2, offices.size())
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(0).getId().toString(), roles.get(0).appRoleId()),
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(0).getApp().getId().toString(), roles.get(0).appId()),
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(0).getApp().getName(), roles.get(0).appName()),
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(0).getName(), roles.get(0).name()),
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(0).getDescription(), roles.get(0).description()),
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(0).isAuthzRole(), roles.get(0).authzRole()),
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(0).getCcmsCode(), roles.get(0).ccmsCode()),
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(0).isLegacySync(), roles.get(0).legacySync()),
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(0).getOrdinal(), roles.get(0).ordinal()),
                () -> Assertions.assertArrayEquals(new String[]{"EXTERNAL", "INTERNAL"}, roles.get(0).appRoleUserTypeRestriction()),
                () -> Assertions.assertArrayEquals(new String[]{FirmType.ADVOCATE.name()}, roles.get(0).firmTypeRestriction()),
                () -> Assertions.assertEquals(userProfile.getAppRoles().stream().toList().get(1).getId().toString(), roles.get(1).appRoleId()),
                () -> Assertions.assertNull(roles.get(1).appRoleUserTypeRestriction()),
                () -> Assertions.assertNull(roles.get(1).firmTypeRestriction()),
                () -> Assertions.assertEquals(userProfile.getOffices().stream().toList().get(0).getId().toString(), offices.get(0).officeId()),
                () -> Assertions.assertEquals(userProfile.getOffices().stream().toList().get(0).getFirm().getId().toString(), offices.get(0).firmId()),
                () -> Assertions.assertEquals(userProfile.getOffices().stream().toList().get(0).getAddress().getPostcode(), offices.get(0).postCode()),
                () -> Assertions.assertEquals(userProfile.getOffices().stream().toList().get(0).getAddress().getAddressLine1(), offices.get(0).addressLine1()),
                () -> Assertions.assertEquals(userProfile.getOffices().stream().toList().get(0).getAddress().getAddressLine2(), offices.get(0).addressLine2()),
                () -> Assertions.assertEquals(userProfile.getOffices().stream().toList().get(0).getAddress().getAddressLine3(), offices.get(0).addressLine3()),
                () -> Assertions.assertEquals(userProfile.getOffices().stream().toList().get(0).getAddress().getCity(), offices.get(0).city()),
                () -> Assertions.assertEquals(userProfile.getOffices().stream().toList().get(1).getId().toString(), offices.get(1).officeId()),
                () -> Assertions.assertNull(offices.get(1).firmId()),
                () -> Assertions.assertNull(offices.get(1).postCode()),
                () -> Assertions.assertNull(offices.get(1).addressLine1()),
                () -> Assertions.assertNull(offices.get(1).addressLine2()),
                () -> Assertions.assertNull(offices.get(1).addressLine3()),
                () -> Assertions.assertNull(offices.get(1).city())
        );
    }

    @Test
    public void testMapShouldHandleNullNestedEntitiesAndEmptyCollections() {
        // Given
        UserProfile userProfile = TestObjectUtil.buildUserProfile();
        userProfile.setActiveProfile(false);
        userProfile.setUserType(UserType.INTERNAL);
        userProfile.setEntraUser(null);
        userProfile.setFirm(null);
        userProfile.setUserProfileStatus(null);
        userProfile.setAppRoles(Set.of());
        userProfile.setOffices(Set.of());

        // When
        GetUserProfileByIdReadModel readModel = readMapper.map(userProfile);

        // Then
        Assertions.assertAll(
                () -> Assertions.assertEquals(userProfile.getId().toString(), readModel.userProfileId()),
                () -> Assertions.assertFalse(readModel.activeProfile()),
                () -> Assertions.assertEquals(UserType.INTERNAL.name(), readModel.userType()),
                () -> Assertions.assertNull(readModel.email()),
                () -> Assertions.assertNull(readModel.fullName()),
                () -> Assertions.assertNull(readModel.firmName()),
                () -> Assertions.assertNull(readModel.firmId()),
                () -> Assertions.assertNull(readModel.multiFirmUser()),
                () -> Assertions.assertNull(readModel.accountStatus()),
                () -> Assertions.assertNull(readModel.profileStatus()),
                () -> Assertions.assertFalse(readModel.hasAppRoles()),
                () -> Assertions.assertNull(readModel.userEntraObjectId()),
                () -> Assertions.assertEquals(userProfile.isUnrestrictedOfficeAccess(), readModel.unrestrictedOfficeAccess()),
                () -> Assertions.assertEquals(userProfile.isLastCcmsSyncSuccessful(), readModel.lastSyncSuccessful()),
                () -> Assertions.assertTrue(readModel.roles().isEmpty()),
                () -> Assertions.assertTrue(readModel.offices().isEmpty())
        );
    }

    private static UserProfile buildRichUserProfile() {
        Firm firm = TestObjectUtil.buildFirm();
        Office officeWithFirmAndAddress = TestObjectUtil.buildOffice();
        officeWithFirmAndAddress.setFirm(firm);
        Office officeWithoutFirmOrAddress = TestObjectUtil.buildOffice(null);
        officeWithoutFirmOrAddress.setFirm(null);

        AppRole appRoleWithRestrictions = TestObjectUtil.buildAppRole();
        appRoleWithRestrictions.setUserTypeRestriction(new UserType[]{UserType.EXTERNAL, UserType.INTERNAL});
        appRoleWithRestrictions.setFirmTypeRestriction(new FirmType[]{FirmType.ADVOCATE});
        appRoleWithRestrictions.setPermissions(Set.of(Permission.VIEW_EXTERNAL_USER));

        AppRole appRoleWithoutRestrictions = TestObjectUtil.buildAppRole();
        appRoleWithoutRestrictions.setUserTypeRestriction(null);
        appRoleWithoutRestrictions.setFirmTypeRestriction(null);
        appRoleWithoutRestrictions.setPermissions(Set.of());

        UserProfile userProfile = TestObjectUtil.buildUserProfile();
        userProfile.setUserType(UserType.EXTERNAL);
        userProfile.setFirm(firm);
        userProfile.setOffices(new LinkedHashSet<>(List.of(officeWithFirmAndAddress, officeWithoutFirmOrAddress)));
        userProfile.setAppRoles(new LinkedHashSet<>(List.of(appRoleWithRestrictions, appRoleWithoutRestrictions)));
        userProfile.setLastCcmsSyncSuccessful(true);
        userProfile.setUnrestrictedOfficeAccess(false);
        return userProfile;
    }
}

