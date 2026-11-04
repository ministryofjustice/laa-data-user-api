package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdAppRoleReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdOfficeReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;

import java.util.List;
import java.util.UUID;

public class GetUserProfileByIdResponseMapperTest {

    private GetUserProfileByIdResponseMapper mapper;

    @BeforeEach
    public void setUp() {
        mapper = new GetUserProfileByIdResponseMapper();
    }

    @Test
    public void testMapShouldConvertAllFieldsAndNestedCollections() {
        // Given
        UUID profileId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID appRoleId1 = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID appRoleId2 = UUID.fromString("33333333-3333-4333-8333-333333333333");
        UUID appId1 = UUID.fromString("44444444-4444-4444-8444-444444444444");
        UUID appId2 = UUID.fromString("55555555-5555-4555-8555-555555555555");
        UUID userEntraObjectId = UUID.fromString("66666666-6666-4666-8666-666666666666");

        GetUserProfileByIdAppRoleReadModel appRoleWithRestrictions = new GetUserProfileByIdAppRoleReadModel(
                appRoleId1,
                appId1,
                "Test App 1",
                "Role 1",
                "Role 1 description",
                Boolean.TRUE,
                "CCMS-1",
                Boolean.FALSE,
                1,
                new UserType[]{UserType.EXTERNAL, UserType.INTERNAL},
                new FirmType[]{FirmType.ADVOCATE}
        );
        GetUserProfileByIdAppRoleReadModel appRoleWithoutRestrictions = new GetUserProfileByIdAppRoleReadModel(
                appRoleId2,
                appId2,
                "Test App 2",
                "Role 2",
                "Role 2 description",
                Boolean.FALSE,
                "CCMS-2",
                Boolean.TRUE,
                2,
                null,
                null
        );
        GetUserProfileByIdOfficeReadModel officeWithAddress = new GetUserProfileByIdOfficeReadModel(
                "office-1",
                "firm-1",
                "SW1A 1AA",
                "Line 1",
                "Line 2",
                "Line 3",
                "London"
        );
        GetUserProfileByIdOfficeReadModel officeWithoutAddress = new GetUserProfileByIdOfficeReadModel(
                "office-2",
                null,
                null,
                null,
                null,
                null,
                null
        );
        GetUserProfileByIdReadModel readModel = GetUserProfileByIdReadModel.builder()
                .userProfileId(profileId)
                .activeProfile(Boolean.TRUE)
                .userType(UserType.EXTERNAL)
                .email("user@example.com")
                .fullName("Test User")
                .firmName("Test Firm")
                .firmId("firm-1")
                .multiFirmUser(Boolean.FALSE)
                .accountStatus(UserAccountStatus.ACTIVE)
                .profileStatus(UserProfileStatus.COMPLETE)
                .hasAppRoles(Boolean.TRUE)
                .userEntraObjectId(userEntraObjectId.toString())
                .unrestrictedOfficeAccess(Boolean.FALSE)
                .lastSyncSuccessful(Boolean.TRUE)
                .roles(List.of(appRoleWithRestrictions, appRoleWithoutRestrictions))
                .offices(List.of(officeWithAddress, officeWithoutAddress))
                .build();

        // When
        ResponseEntity<UserProfileDetailView> response = mapper.map(readModel);

        // Then
        UserProfileDetailView view = response.getBody();
        Assertions.assertNotNull(view);
        List<AppRoleView> roles = List.copyOf(view.roles());
        List<OfficeView> offices = List.copyOf(view.offices());

        Assertions.assertAll(
                () -> Assertions.assertEquals(200, response.getStatusCode().value()),
                () -> Assertions.assertEquals(profileId, view.userProfileId()),
                () -> Assertions.assertTrue(view.activeProfile()),
                () -> Assertions.assertEquals(UserType.EXTERNAL, view.userType()),
                () -> Assertions.assertEquals("user@example.com", view.email()),
                () -> Assertions.assertEquals("Test User", view.fullName()),
                () -> Assertions.assertEquals("Test Firm", view.firmName()),
                () -> Assertions.assertEquals("firm-1", view.firmId()),
                () -> Assertions.assertFalse(view.multiFirmUser()),
                () -> Assertions.assertEquals(UserAccountStatus.ACTIVE, view.accountStatus()),
                () -> Assertions.assertEquals(UserProfileStatus.COMPLETE, view.profileStatus()),
                () -> Assertions.assertTrue(view.hasAppRoles()),
                () -> Assertions.assertEquals(userEntraObjectId, view.userEntraObjectId()),
                () -> Assertions.assertFalse(view.unrestrictedOfficeAccess()),
                () -> Assertions.assertTrue(view.lastSyncSuccessful()),
                () -> Assertions.assertEquals(2, roles.size()),
                () -> Assertions.assertEquals(2, offices.size())
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(appRoleId1.toString(), roles.getFirst().appRoleId()),
                () -> Assertions.assertEquals(appId1.toString(), roles.getFirst().appId()),
                () -> Assertions.assertEquals("Test App 1", roles.getFirst().appName()),
                () -> Assertions.assertEquals("Role 1", roles.getFirst().name()),
                () -> Assertions.assertEquals("Role 1 description", roles.getFirst().description()),
                () -> Assertions.assertTrue(roles.getFirst().authzRole()),
                () -> Assertions.assertEquals("CCMS-1", roles.getFirst().roleIdentifier()),
                () -> Assertions.assertFalse(roles.getFirst().legacySync()),
                () -> Assertions.assertEquals(1, roles.getFirst().ordinal()),
                () -> Assertions.assertEquals("BOTH", roles.getFirst().appRoleUserTypeRestriction()),
                () -> Assertions.assertEquals(List.of("ADVOCATE"), roles.getFirst().firmTypeRestriction()),
                () -> Assertions.assertEquals(appRoleId2.toString(), roles.get(1).appRoleId()),
                () -> Assertions.assertEquals("BOTH", roles.get(1).appRoleUserTypeRestriction()),
                () -> Assertions.assertTrue(roles.get(1).firmTypeRestriction().isEmpty())
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals("office-1", offices.getFirst().officeId()),
                () -> Assertions.assertEquals("firm-1", offices.getFirst().firmId()),
                () -> Assertions.assertEquals("SW1A 1AA", offices.getFirst().postCode()),
                () -> Assertions.assertEquals("Line 1", offices.getFirst().addressLine1()),
                () -> Assertions.assertEquals("Line 2", offices.getFirst().addressLine2()),
                () -> Assertions.assertEquals("Line 3", offices.getFirst().addressLine3()),
                () -> Assertions.assertEquals("London", offices.getFirst().city()),
                () -> Assertions.assertEquals("office-2", offices.get(1).officeId()),
                () -> Assertions.assertNull(offices.get(1).firmId()),
                () -> Assertions.assertNull(offices.get(1).postCode()),
                () -> Assertions.assertNull(offices.get(1).addressLine1()),
                () -> Assertions.assertNull(offices.get(1).addressLine2()),
                () -> Assertions.assertNull(offices.get(1).addressLine3()),
                () -> Assertions.assertNull(offices.get(1).city())
        );
    }
}
