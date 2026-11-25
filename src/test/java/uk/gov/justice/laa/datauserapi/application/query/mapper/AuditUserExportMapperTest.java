package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileSilasStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.AppType;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuditUserExportMapperTest {

    private final AuditUserExportMapper mapper = new AuditUserExportMapper();

    @Test
    void toRow_mapsNameEmailFirmAndStatusFields() {
        EntraUser entraUser = EntraUser.builder()
                .firstName("Ann")
                .lastName("Example")
                .email("ann@example.com")
                .multiFirmUser(true)
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .build();

        Firm firm = Firm.builder().name("Example Firm").code("FIRM001").build();

        UserProfile profile = UserProfile.builder()
                .entraUser(entraUser)
                .firm(firm)
                .userType(UserType.EXTERNAL)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .silasStatus(UserProfileSilasStatus.COMPLETE)
                .appRoles(Set.of())
                .build();

        AuditUserExportRow row = mapper.toRow(profile);

        assertThat(row.name()).isEqualTo("Ann Example");
        assertThat(row.email()).isEqualTo("ann@example.com");
        assertThat(row.firmName()).isEqualTo("Example Firm");
        assertThat(row.firmId()).isEqualTo("FIRM001");
        assertThat(row.multiFirm()).isEqualTo("Yes");
        assertThat(row.silasAccountStatus()).isEqualTo("Active");
        assertThat(row.providerAdmin()).isEqualTo("No");
        assertThat(row.appAccess()).isEmpty();
        assertThat(row.rolesAssigned()).isEmpty();
    }

    @Test
    void toRow_withNoFirm_rendersEmptyFirmFields() {
        EntraUser entraUser = EntraUser.builder()
                .firstName("Ian")
                .lastName("Internal")
                .email("ian@internal.example")
                .multiFirmUser(false)
                .userAccountStatus(UserAccountStatus.ACTIVATION_REQUIRED)
                .build();

        UserProfile profile = UserProfile.builder()
                .entraUser(entraUser)
                .firm(null)
                .userType(UserType.INTERNAL)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .silasStatus(UserProfileSilasStatus.COMPLETE)
                .appRoles(Set.of())
                .build();

        AuditUserExportRow row = mapper.toRow(profile);

        assertThat(row.firmName()).isEmpty();
        assertThat(row.firmId()).isEmpty();
        assertThat(row.multiFirm()).isEqualTo("No");
        assertThat(row.silasAccountStatus()).isEqualTo("Activation Required");
    }

    @Test
    void toRow_withProviderAdminAuthzRole_reportsProviderAdminYes() {
        App app = App.builder().name("LAA App").description("desc").appType(AppType.LAA)
                .url("https://example.com").securityGroupOid("sg-oid").build();

        AppRole providerAdminRole = AppRole.builder()
                .name("Firm User Manager")
                .roleIdentifier("FIRM_USER_MANAGER")
                .description("Firm User Manager role")
                .authzRole(true)
                .app(app)
                .build();

        AppRole otherRole = AppRole.builder()
                .name("Billing")
                .roleIdentifier("BILLING")
                .description("Billing role")
                .authzRole(false)
                .app(app)
                .build();

        EntraUser entraUser = EntraUser.builder()
                .firstName("Pat")
                .lastName("Admin")
                .email("pat@example.com")
                .multiFirmUser(false)
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .build();

        Firm firm = Firm.builder().name("Example Firm").code("FIRM001").build();

        UserProfile profile = UserProfile.builder()
                .entraUser(entraUser)
                .firm(firm)
                .userType(UserType.EXTERNAL)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .silasStatus(UserProfileSilasStatus.COMPLETE)
                .appRoles(Set.of(providerAdminRole, otherRole))
                .build();

        AuditUserExportRow row = mapper.toRow(profile);

        assertThat(row.providerAdmin()).isEqualTo("Yes");
        assertThat(row.appAccess()).isEqualTo("LAA App");
        assertThat(row.rolesAssigned()).isEqualTo("Billing, Firm User Manager");
    }
}
