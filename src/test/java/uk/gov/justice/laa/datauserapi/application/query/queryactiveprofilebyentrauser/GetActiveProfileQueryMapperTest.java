package uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileSilasStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AppDto;
import uk.gov.justice.laa.datauserapi.dto.AppRoleDto;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.FirmDto;
import uk.gov.justice.laa.datauserapi.dto.OfficeDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetActiveProfileQueryMapperTest {

    private final GetActiveProfileQueryMapper mapper = new GetActiveProfileQueryMapper();
    private static final String USER_ENTRA_OID = UUID.randomUUID().toString();

    @Test
    void mapsUserProfileToDetailView() {
        UserProfileDto profile = createBasicUserProfile();

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.userProfileId()).isEqualTo(profile.getId());
        assertThat(result.activeProfile()).isTrue();
        assertThat(result.userType()).isEqualTo(UserType.INTERNAL);
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.fullName()).isEqualTo("John Doe");
    }

    @Test
    void mapsAccountAndProfileStatus() {
        UserProfileDto profile = createBasicUserProfile();

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.accountStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(result.profileStatus()).isEqualTo(UserProfileSilasStatus.COMPLETE);
    }

    @Test
    void includesFirmDetailsWhenPresent() {
        FirmDto firm = mock(FirmDto.class);
        when(firm.getId()).thenReturn(UUID.randomUUID());
        when(firm.getName()).thenReturn("Test Firm");

        UserProfileDto profile = createUserProfileWithFirm(firm);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.firmName()).isEqualTo("Test Firm");
        assertThat(result.firmId()).isEqualTo(firm.getId().toString());
    }

    @Test
    void handlesMissingFirmForInternalProfile() {
        UserProfileDto profile = createBasicUserProfile();
        when(profile.getFirm()).thenReturn(null);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.firmName()).isEmpty();
        assertThat(result.firmId()).isEmpty();
    }

    @Test
    void mapsAppRoles() {
        AppRoleDto role1 = createAppRole("Admin", "ADMIN");
        AppRoleDto role2 = createAppRole("Editor", "EDITOR");

        UserProfileDto profile = createBasicUserProfile();
        when(profile.getAppRoles()).thenReturn(List.of(role1, role2));

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.hasAppRoles()).isTrue();
        assertThat(result.roles()).hasSize(2);
    }

    @Test
    void mapsOffices() {
        OfficeDto office1 = createOffice("Office1", "1000");
        OfficeDto office2 = createOffice("Office2", "2000");

        UserProfileDto profile = createBasicUserProfile();
        when(profile.getOffices()).thenReturn(List.of(office1, office2));

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.offices()).hasSize(2);
    }

    @Test
    void mapsOfficeDetailsCorrectly() {
        UUID firmId = UUID.randomUUID();

        OfficeDto.AddressDto address = mock(OfficeDto.AddressDto.class);
        when(address.getPostcode()).thenReturn("SW1A 1AA");
        when(address.getAddressLine1()).thenReturn("123 Main St");
        when(address.getAddressLine2()).thenReturn("Suite 100");
        when(address.getAddressLine3()).thenReturn("Floor 3");
        when(address.getCity()).thenReturn("London");

        OfficeDto office = mock(OfficeDto.class);
        UUID officeId = UUID.randomUUID();
        when(office.getId()).thenReturn(officeId);
        when(office.getFirmId()).thenReturn(firmId.toString());
        when(office.getAddress()).thenReturn(address);

        UserProfileDto profile = createBasicUserProfile();
        when(profile.getOffices()).thenReturn(List.of(office));

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        OfficeView officeView = result.offices().getFirst();
        assertThat(officeView.officeId()).isEqualTo(officeId.toString());
        assertThat(officeView.firmId()).isEqualTo(firmId.toString());
        assertThat(officeView.postCode()).isEqualTo("SW1A 1AA");
        assertThat(officeView.addressLine1()).isEqualTo("123 Main St");
        assertThat(officeView.city()).isEqualTo("London");
    }

    @Test
    void indicatesNoRolesWhenEmpty() {
        UserProfileDto profile = createBasicUserProfile();
        when(profile.getAppRoles()).thenReturn(new ArrayList<>());

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.hasAppRoles()).isFalse();
        assertThat(result.roles()).isEmpty();
    }

    @Test
    void mapsUnrestrictedOfficeAccess() {
        UserProfileDto profile = createBasicUserProfile();
        when(profile.isUnrestrictedOfficeAccess()).thenReturn(true);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.unrestrictedOfficeAccess()).isTrue();
    }

    @Test
    void mapsLastSyncSuccessfulStatus() {
        UserProfileDto profile = createBasicUserProfile();
        when(profile.isLastCcmsSyncSuccessful()).thenReturn(false);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.lastSyncSuccessful()).isFalse();
    }

    @Test
    void mapsUserEntraObjectId() {
        UserProfileDto profile = createBasicUserProfile();

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.userEntraObjectId().toString()).isEqualTo(USER_ENTRA_OID);
    }

    @Test
    void mapsMultiFirmUserIndicator() {
        EntraUserDto user = mock(EntraUserDto.class);
        when(user.getEntraOid()).thenReturn(USER_ENTRA_OID);
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getFirstName()).thenReturn("John");
        when(user.getLastName()).thenReturn("Doe");
        when(user.getUserAccountStatus()).thenReturn(UserAccountStatus.ACTIVE);
        when(user.isMultiFirmUser()).thenReturn(true);

        UserProfileDto profile = createBasicUserProfile();
        when(profile.getEntraUser()).thenReturn(user);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.multiFirmUser()).isTrue();
    }

    @Test
    void mapsAppRoleDetails() {
        AppRoleDto role = createAppRole("Manager", "MGR");

        UserProfileDto profile = createBasicUserProfile();
        when(profile.getAppRoles()).thenReturn(List.of(role));

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        AppRoleView roleView = result.roles().getFirst();
        assertThat(roleView.name()).isEqualTo("Manager");
        assertThat(roleView.description()).isEqualTo("Manager Role");
    }

    // Helper methods
    private UserProfileDto createBasicUserProfile() {
        EntraUserDto user = mock(EntraUserDto.class);
        when(user.getEntraOid()).thenReturn(USER_ENTRA_OID);
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getFirstName()).thenReturn("John");
        when(user.getLastName()).thenReturn("Doe");
        when(user.getUserAccountStatus()).thenReturn(UserAccountStatus.ACTIVE);
        when(user.isMultiFirmUser()).thenReturn(false);

        UserProfileDto profile = mock(UserProfileDto.class);
        when(profile.getId()).thenReturn(UUID.randomUUID());
        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(UserType.INTERNAL);
        when(profile.getEntraUser()).thenReturn(user);
        when(profile.getSilasStatus()).thenReturn(UserProfileSilasStatus.COMPLETE);
        when(profile.getAppRoles()).thenReturn(List.of());
        when(profile.getOffices()).thenReturn(List.of());
        when(profile.getFirm()).thenReturn(null);
        when(profile.isUnrestrictedOfficeAccess()).thenReturn(false);
        when(profile.isLastCcmsSyncSuccessful()).thenReturn(true);

        return profile;
    }

    private UserProfileDto createUserProfileWithFirm(FirmDto firm) {
        UserProfileDto profile = createBasicUserProfile();
        when(profile.getUserType()).thenReturn(UserType.EXTERNAL);
        when(profile.getFirm()).thenReturn(firm);
        return profile;
    }

    private AppRoleDto createAppRole(String name, String ccmsCode) {
        AppDto app = mock(AppDto.class);
        String appId = UUID.randomUUID().toString();
        when(app.getId()).thenReturn(appId);
        when(app.getName()).thenReturn("Test App");

        AppRoleDto role = mock(AppRoleDto.class);
        when(role.getId()).thenReturn(UUID.randomUUID().toString());
        when(role.getApp()).thenReturn(app);
        when(role.getName()).thenReturn(name);
        when(role.getDescription()).thenReturn(name + " Role");
        when(role.isAuthzRole()).thenReturn(true);
        when(role.isLegacySync()).thenReturn(false);
        when(role.getOrdinal()).thenReturn(1);
        when(role.getUserTypeRestriction()).thenReturn(null);
        when(role.getFirmTypeRestriction()).thenReturn(null);

        return role;
    }

    private OfficeDto createOffice(String code, String postcode) {
        String firmId = UUID.randomUUID().toString();

        OfficeDto.AddressDto address = mock(OfficeDto.AddressDto.class);
        when(address.getPostcode()).thenReturn(postcode);
        when(address.getAddressLine1()).thenReturn("Test Address");
        when(address.getAddressLine2()).thenReturn("");
        when(address.getAddressLine3()).thenReturn("");
        when(address.getCity()).thenReturn("London");

        OfficeDto office = mock(OfficeDto.class);
        when(office.getId()).thenReturn(UUID.randomUUID());
        when(office.getFirmId()).thenReturn(firmId);
        when(office.getAddress()).thenReturn(address);

        return office;
    }
}
