package uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetActiveProfileQueryMapperTest {

    private final GetActiveProfileQueryMapper mapper = new GetActiveProfileQueryMapper();
    private static final String USER_ENTRA_OID = UUID.randomUUID().toString();

    @Test
    void mapsUserProfileToDetailView() {
        UserProfile profile = createBasicUserProfile();

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.userProfileId()).isEqualTo(profile.getId());
        assertThat(result.activeProfile()).isTrue();
        assertThat(result.userType()).isEqualTo(UserType.INTERNAL);
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.fullName()).isEqualTo("John Doe");
    }

    @Test
    void mapsAccountAndProfileStatus() {
        UserProfile profile = createBasicUserProfile();

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.accountStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(result.profileStatus()).isEqualTo(UserProfileStatus.COMPLETE);
    }

    @Test
    void includesFirmDetailsWhenPresent() {
        Firm firm = mock(Firm.class);
        when(firm.getId()).thenReturn(UUID.randomUUID());
        when(firm.getName()).thenReturn("Test Firm");

        UserProfile profile = createUserProfileWithFirm(firm);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.firmName()).isEqualTo("Test Firm");
        assertThat(result.firmId()).isEqualTo(firm.getId().toString());
    }

    @Test
    void handlesMissingFirmForInternalProfile() {
        UserProfile profile = createBasicUserProfile();
        when(profile.getFirm()).thenReturn(null);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.firmName()).isEmpty();
        assertThat(result.firmId()).isEmpty();
    }

    @Test
    void mapsAppRoles() {
        AppRole role1 = createAppRole("Admin", "ADMIN");
        AppRole role2 = createAppRole("Editor", "EDITOR");

        UserProfile profile = createBasicUserProfile();
        when(profile.getAppRoles()).thenReturn(Set.of(role1, role2));

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.hasAppRoles()).isTrue();
        assertThat(result.roles()).hasSize(2);
    }

    @Test
    void mapsOffices() {
        Office office1 = createOffice("Office1", "1000");
        Office office2 = createOffice("Office2", "2000");

        UserProfile profile = createBasicUserProfile();
        when(profile.getOffices()).thenReturn(Set.of(office1, office2));

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.offices()).hasSize(2);
    }

    @Test
    void mapsOfficeDetailsCorrectly() {
        Firm firm = mock(Firm.class);
        UUID firmId = UUID.randomUUID();
        when(firm.getId()).thenReturn(firmId);

        Office.Address address = mock(Office.Address.class);
        when(address.getPostcode()).thenReturn("SW1A 1AA");
        when(address.getAddressLine1()).thenReturn("123 Main St");
        when(address.getAddressLine2()).thenReturn("Suite 100");
        when(address.getAddressLine3()).thenReturn("Floor 3");
        when(address.getCity()).thenReturn("London");

        Office office = mock(Office.class);
        UUID officeId = UUID.randomUUID();
        when(office.getId()).thenReturn(officeId);
        when(office.getFirm()).thenReturn(firm);
        when(office.getAddress()).thenReturn(address);

        UserProfile profile = createBasicUserProfile();
        when(profile.getOffices()).thenReturn(Set.of(office));

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
        UserProfile profile = createBasicUserProfile();
        when(profile.getAppRoles()).thenReturn(new HashSet<>());

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.hasAppRoles()).isFalse();
        assertThat(result.roles()).isEmpty();
    }

    @Test
    void mapsUnrestrictedOfficeAccess() {
        UserProfile profile = createBasicUserProfile();
        when(profile.isUnrestrictedOfficeAccess()).thenReturn(true);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.unrestrictedOfficeAccess()).isTrue();
    }

    @Test
    void mapsLastSyncSuccessfulStatus() {
        UserProfile profile = createBasicUserProfile();
        when(profile.isLastCcmsSyncSuccessful()).thenReturn(false);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.lastSyncSuccessful()).isFalse();
    }

    @Test
    void mapsUserEntraObjectId() {
        UserProfile profile = createBasicUserProfile();

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.userEntraObjectId().toString()).isEqualTo(USER_ENTRA_OID);
    }

    @Test
    void mapsMultiFirmUserIndicator() {
        EntraUser user = mock(EntraUser.class);
        when(user.getEntraOid()).thenReturn(USER_ENTRA_OID);
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getFirstName()).thenReturn("John");
        when(user.getLastName()).thenReturn("Doe");
        when(user.getUserAccountStatus()).thenReturn(UserAccountStatus.ACTIVE);
        when(user.isMultiFirmUser()).thenReturn(true);

        UserProfile profile = createBasicUserProfile();
        when(profile.getEntraUser()).thenReturn(user);

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        assertThat(result.multiFirmUser()).isTrue();
    }

    @Test
    void mapsAppRoleDetails() {
        AppRole role = createAppRole("Manager", "MGR");

        UserProfile profile = createBasicUserProfile();
        when(profile.getAppRoles()).thenReturn(Set.of(role));

        UserProfileDetailView result = mapper.mapProfileToDetailView(profile);

        AppRoleView roleView = result.roles().getFirst();
        assertThat(roleView.name()).isEqualTo("Manager");
        assertThat(roleView.description()).isEqualTo("Manager Role");
    }

    // Helper methods
    private UserProfile createBasicUserProfile() {
        EntraUser user = mock(EntraUser.class);
        when(user.getEntraOid()).thenReturn(USER_ENTRA_OID);
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getFirstName()).thenReturn("John");
        when(user.getLastName()).thenReturn("Doe");
        when(user.getUserAccountStatus()).thenReturn(UserAccountStatus.ACTIVE);
        when(user.isMultiFirmUser()).thenReturn(false);

        UserProfile profile = mock(UserProfile.class);
        when(profile.getId()).thenReturn(UUID.randomUUID());
        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(UserType.INTERNAL);
        when(profile.getEntraUser()).thenReturn(user);
        when(profile.getUserProfileStatus()).thenReturn(UserProfileStatus.COMPLETE);
        when(profile.getAppRoles()).thenReturn(new HashSet<>());
        when(profile.getOffices()).thenReturn(new HashSet<>());
        when(profile.getFirm()).thenReturn(null);
        when(profile.isUnrestrictedOfficeAccess()).thenReturn(false);
        when(profile.isLastCcmsSyncSuccessful()).thenReturn(true);

        return profile;
    }

    private UserProfile createUserProfileWithFirm(Firm firm) {
        UserProfile profile = createBasicUserProfile();
        when(profile.getUserType()).thenReturn(UserType.EXTERNAL);
        when(profile.getFirm()).thenReturn(firm);
        return profile;
    }

    private AppRole createAppRole(String name, String ccmsCode) {
        App app = mock(App.class);
        UUID appId = UUID.randomUUID();
        when(app.getId()).thenReturn(appId);
        when(app.getName()).thenReturn("Test App");

        AppRole role = mock(AppRole.class);
        when(role.getId()).thenReturn(UUID.randomUUID());
        when(role.getApp()).thenReturn(app);
        when(role.getName()).thenReturn(name);
        when(role.getDescription()).thenReturn(name + " Role");
        when(role.isAuthzRole()).thenReturn(true);
        when(role.getCcmsCode()).thenReturn(ccmsCode);
        when(role.isLegacySync()).thenReturn(false);
        when(role.getOrdinal()).thenReturn(1);
        when(role.getUserTypeRestriction()).thenReturn(null);
        when(role.getFirmTypeRestriction()).thenReturn(null);

        return role;
    }

    private Office createOffice(String code, String postcode) {
        Firm firm = mock(Firm.class);
        when(firm.getId()).thenReturn(UUID.randomUUID());

        Office.Address address = mock(Office.Address.class);
        when(address.getPostcode()).thenReturn(postcode);
        when(address.getAddressLine1()).thenReturn("Test Address");
        when(address.getAddressLine2()).thenReturn("");
        when(address.getAddressLine3()).thenReturn("");
        when(address.getCity()).thenReturn("London");

        Office office = mock(Office.class);
        when(office.getId()).thenReturn(UUID.randomUUID());
        when(office.getFirm()).thenReturn(firm);
        when(office.getAddress()).thenReturn(address);

        return office;
    }
}
