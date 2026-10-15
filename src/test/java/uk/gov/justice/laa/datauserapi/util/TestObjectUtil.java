package uk.gov.justice.laa.datauserapi.util;

import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.AppType;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.model.UserProfileSilasStatus;

import java.util.Set;
import java.util.UUID;

public class TestObjectUtil {

    public static UserProfile buildUserProfile() {
        return buildUserProfile(buildEntraUser(), buildOffice(), buildAppRole(), buildFirm());
    }

    public static UserProfile buildUserProfile(EntraUser entraUser, Office office, AppRole appRole, Firm firm) {
        return UserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .entraUser(entraUser)
                .offices(Set.of(office))
                .appRoles(Set.of(appRole))
                .silasStatus(UserProfileSilasStatus.COMPLETE)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .legacyUserId(UUID.randomUUID())
                .firm(firm)
                .build();
    }

    public static Firm buildFirm() {
        return Firm.builder()
                .id(UUID.randomUUID())
                .name("Test firm")
                .code("TEST-FIRM-CODE")
                .build();
    }

    public static Office buildOffice() {
        Office.Address address = buildOfficeAddress();
        return buildOffice(address);
    }

    public static Office buildOffice(Office.Address address) {
        return Office.builder()
                .id(UUID.randomUUID())
                .code("TEST-OFFICE-CODE")
                .address(address)
                .build();
    }

    public static Office.Address buildOfficeAddress() {
        return Office.Address.builder()
                .addressLine1("Test address line 1")
                .addressLine2("Test address line 2")
                .addressLine3("Test address line 3")
                .city("Test city")
                .postcode("Test postcode")
                .build();
    }

    public static AppRole buildAppRole() {
        App app = buildApp();
        return buildAppRole(app);
    }

    public static AppRole buildAppRole(App app) {
        return AppRole.builder()
                .id(UUID.randomUUID())
                .name("Test app role")
                .description("A test app role.")
                .authzRole(false)
                .legacySync(true)
                .permissions(Set.of(Permission.VIEW_EXTERNAL_USER))
                .ccmsCode("CCMS CODE")
                .firmTypeRestriction(new FirmType[]{FirmType.ADVOCATE})
                .userTypeRestriction(new UserType[]{UserType.EXTERNAL})
                .app(app)
                .build();
    }

    public static App buildApp() {
        return App.builder()
                .id(UUID.randomUUID())
                .name("Test App")
                .description("A test app.")
                .entraOid(UUID.randomUUID().toString())
                .entraAppId(UUID.randomUUID().toString())
                .ordinal(1)
                .appType(AppType.LAA)
                .securityGroupOid(UUID.randomUUID().toString())
                .url("testapp.test")
                .build();
    }

    public static EntraUser buildEntraUser() {
        return EntraUser.builder()
                .id(UUID.randomUUID())
                .email("test@test.com")
                .active(true)
                .firstName("Test")
                .lastName("User")
                .entraOid(UUID.randomUUID().toString())
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .build();
    }
}
