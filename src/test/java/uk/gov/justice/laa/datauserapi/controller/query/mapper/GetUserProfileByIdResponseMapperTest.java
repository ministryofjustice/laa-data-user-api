package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdAppRoleReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdOfficeReadModel;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailsAppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailsOfficeView;

import java.util.List;

public class GetUserProfileByIdResponseMapperTest {

    private GetUserProfileByIdResponseMapper mapper;

    @BeforeEach
    public void setUp() {
        mapper = new GetUserProfileByIdResponseMapper();
    }

    @Test
    public void testMapShouldConvertAllFieldsAndNestedCollections() {
        // Given
        GetUserProfileByIdAppRoleReadModel appRoleWithRestrictions = new GetUserProfileByIdAppRoleReadModel(
                "app-role-1",
                "app-1",
                "Test App 1",
                "Role 1",
                "Role 1 description",
                Boolean.TRUE,
                "CCMS-1",
                Boolean.FALSE,
                1,
                new String[]{"EXTERNAL", "INTERNAL"},
                new String[]{"ADVOCATE"}
        );
        GetUserProfileByIdAppRoleReadModel appRoleWithoutRestrictions = new GetUserProfileByIdAppRoleReadModel(
                "app-role-2",
                "app-2",
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
                .userProfileId("profile-1")
                .activeProfile(Boolean.TRUE)
                .userType("EXTERNAL")
                .email("user@example.com")
                .fullName("Test User")
                .firmName("Test Firm")
                .firmId("firm-1")
                .multiFirmUser(Boolean.FALSE)
                .accountStatus("ACTIVE")
                .profileStatus("COMPLETE")
                .hasAppRoles(Boolean.TRUE)
                .userEntraObjectId("entra-oid-1")
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
        List<UserProfileDetailsAppRoleView> roles = List.copyOf(view.roles());
        List<UserProfileDetailsOfficeView> offices = List.copyOf(view.offices());

        Assertions.assertAll(
                () -> Assertions.assertEquals(200, response.getStatusCode().value()),
                () -> Assertions.assertEquals("profile-1", view.userProfileId()),
                () -> Assertions.assertEquals(Boolean.TRUE, view.activeProfile()),
                () -> Assertions.assertEquals("EXTERNAL", view.userType()),
                () -> Assertions.assertEquals("user@example.com", view.email()),
                () -> Assertions.assertEquals("Test User", view.fullName()),
                () -> Assertions.assertEquals("Test Firm", view.firmName()),
                () -> Assertions.assertEquals("firm-1", view.firmId()),
                () -> Assertions.assertEquals(Boolean.FALSE, view.multiFirmUser()),
                () -> Assertions.assertEquals("ACTIVE", view.accountStatus()),
                () -> Assertions.assertEquals("COMPLETE", view.profileStatus()),
                () -> Assertions.assertEquals(Boolean.TRUE, view.hasAppRoles()),
                () -> Assertions.assertEquals("entra-oid-1", view.userEntraObjectId()),
                () -> Assertions.assertEquals(Boolean.FALSE, view.unrestrictedOfficeAccess()),
                () -> Assertions.assertEquals(Boolean.TRUE, view.lastSyncSuccessful()),
                () -> Assertions.assertEquals(2, roles.size()),
                () -> Assertions.assertEquals(2, offices.size())
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals("app-role-1", roles.getFirst().appRoleId()),
                () -> Assertions.assertEquals("app-1", roles.getFirst().appId()),
                () -> Assertions.assertEquals("Test App 1", roles.getFirst().appName()),
                () -> Assertions.assertEquals("Role 1", roles.getFirst().name()),
                () -> Assertions.assertEquals("Role 1 description", roles.getFirst().description()),
                () -> Assertions.assertEquals(Boolean.TRUE, roles.getFirst().authzRole()),
                () -> Assertions.assertEquals("CCMS-1", roles.getFirst().ccmsCode()),
                () -> Assertions.assertEquals(Boolean.FALSE, roles.getFirst().legacySync()),
                () -> Assertions.assertEquals(1, roles.getFirst().ordinal()),
                () -> Assertions.assertArrayEquals(new String[]{"EXTERNAL", "INTERNAL"}, roles.getFirst().appRoleUserTypeRestriction()),
                () -> Assertions.assertArrayEquals(new String[]{"ADVOCATE"}, roles.getFirst().firmTypeRestriction()),
                () -> Assertions.assertEquals("app-role-2", roles.get(1).appRoleId()),
                () -> Assertions.assertNull(roles.get(1).appRoleUserTypeRestriction()),
                () -> Assertions.assertNull(roles.get(1).firmTypeRestriction())
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

