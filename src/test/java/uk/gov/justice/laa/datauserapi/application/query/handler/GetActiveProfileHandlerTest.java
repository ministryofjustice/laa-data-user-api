package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveProfileHandlerTest {

    @Mock
    private UserProfileQueryRepository userProfileQueryRepository;

    private static final String ACTOR_OID = UUID.randomUUID().toString();
    private static final String USER_ENTRA_OBJECT_ID = UUID.randomUUID().toString();

    @Test
    void returnsActiveProfileDetailView() {
        UserProfile userProfile = createUserProfile(UserType.INTERNAL, "test@example.com");
        GetActiveProfileQuery query = new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(userProfile));

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository);
        UserProfileDetailView result = handler.handle(query);

        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.activeProfile()).isTrue();
        assertThat(result.userType()).isEqualTo(UserType.INTERNAL);
        assertThat(result.accountStatus()).isEqualTo(UserAccountStatus.ACTIVE);
    }

    @Test
    void throwsNotFoundWhenProfileDoesNotExist() {
        GetActiveProfileQuery query = new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.empty());

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository);

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active profile not found");
    }

    @Test
    void mapsRolesToDetailView() {
        UserProfile userProfile = createUserProfileWithRoles(UserType.INTERNAL, 2);
        GetActiveProfileQuery query = new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(userProfile));

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository);
        UserProfileDetailView result = handler.handle(query);

        assertThat(result.hasAppRoles()).isTrue();
        assertThat(result.roles()).hasSize(2);
    }

    @Test
    void mapsOffices() {
        UserProfile userProfile = createUserProfileWithOffices(UserType.INTERNAL, 1);
        GetActiveProfileQuery query = new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(userProfile));

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository);
        UserProfileDetailView result = handler.handle(query);

        assertThat(result.offices()).hasSize(1);
    }

    @Test
    void includesFirmDetailsForExternalUser() {
        Firm firm = createFirm();
        UserProfile userProfile = createUserProfileWithFirm(UserType.EXTERNAL, firm);
        GetActiveProfileQuery query = new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(userProfile));

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository);
        UserProfileDetailView result = handler.handle(query);

        assertThat(result.firmName()).isEqualTo(firm.getName());
        assertThat(result.firmId()).isEqualTo(firm.getId().toString());
    }

    @Test
    void handlesNullFirmForInternalUser() {
        UserProfile userProfile = createUserProfile(UserType.INTERNAL, "internal@example.com");
        GetActiveProfileQuery query = new GetActiveProfileQuery(USER_ENTRA_OBJECT_ID, ACTOR_OID);

        when(userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(
                USER_ENTRA_OBJECT_ID, ACTOR_OID))
                .thenReturn(Optional.of(userProfile));

        GetActiveProfileHandler handler = new GetActiveProfileHandler(userProfileQueryRepository);
        UserProfileDetailView result = handler.handle(query);

        assertThat(result.firmName()).isEmpty();
        assertThat(result.firmId()).isEmpty();
    }

    private UserProfile createUserProfile(UserType userType, String email) {
        EntraUser user = mock(EntraUser.class);
        when(user.getEntraOid()).thenReturn(USER_ENTRA_OBJECT_ID);
        when(user.getEmail()).thenReturn(email);
        when(user.getFirstName()).thenReturn("Test");
        when(user.getLastName()).thenReturn("User");
        when(user.getUserAccountStatus()).thenReturn(UserAccountStatus.ACTIVE);
        when(user.isMultiFirmUser()).thenReturn(false);

        UserProfile profile = mock(UserProfile.class);
        when(profile.getId()).thenReturn(UUID.randomUUID());
        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(userType);
        when(profile.getEntraUser()).thenReturn(user);
        when(profile.getUserProfileStatus()).thenReturn(UserProfileStatus.COMPLETE);
        when(profile.getAppRoles()).thenReturn(new HashSet<>());
        when(profile.getOffices()).thenReturn(new HashSet<>());
        when(profile.getFirm()).thenReturn(null);
        when(profile.isUnrestrictedOfficeAccess()).thenReturn(false);
        when(profile.isLastCcmsSyncSuccessful()).thenReturn(true);

        return profile;
    }

    private UserProfile createUserProfileWithRoles(UserType userType, int roleCount) {
        UserProfile profile = createUserProfile(userType, "user@example.com");
        Set<AppRole> roles = new HashSet<>();
        for (int i = 0; i < roleCount; i++) {
            roles.add(createAppRole("Role" + i, "ROLE" + i));
        }
        when(profile.getAppRoles()).thenReturn(roles);

        return profile;
    }

    private AppRole createAppRole(String name, String ccmsCode) {
        uk.gov.justice.laa.datauserapi.entity.App app = mock(uk.gov.justice.laa.datauserapi.entity.App.class);
        when(app.getId()).thenReturn(UUID.randomUUID());
        when(app.getName()).thenReturn("Test App");

        AppRole role = mock(AppRole.class);
        when(role.getId()).thenReturn(UUID.randomUUID());
        when(role.getApp()).thenReturn(app);
        when(role.getName()).thenReturn(name);
        when(role.getDescription()).thenReturn(name + " Description");
        when(role.isAuthzRole()).thenReturn(true);
        when(role.getCcmsCode()).thenReturn(ccmsCode);
        when(role.isLegacySync()).thenReturn(false);
        when(role.getOrdinal()).thenReturn(1);
        when(role.getUserTypeRestriction()).thenReturn(null);
        when(role.getFirmTypeRestriction()).thenReturn(null);

        return role;
    }

    private UserProfile createUserProfileWithOffices(UserType userType, int officeCount) {
        UserProfile profile = createUserProfile(userType, "user@example.com");
        Firm firm = createFirm();
        Set<Office> offices = new HashSet<>();
        for (int i = 0; i < officeCount; i++) {
            offices.add(createOffice(firm));
        }
        when(profile.getOffices()).thenReturn(offices);
        when(profile.getFirm()).thenReturn(firm);
        return profile;
    }

    private UserProfile createUserProfileWithFirm(UserType userType, Firm firm) {
        UserProfile profile = createUserProfile(userType, "user@example.com");
        when(profile.getFirm()).thenReturn(firm);
        return profile;
    }

    private Firm createFirm() {
        Firm firm = mock(Firm.class);
        when(firm.getId()).thenReturn(UUID.randomUUID());
        when(firm.getName()).thenReturn("Test Firm");
        return firm;
    }

    private Office createOffice(Firm firm) {
        Office office = mock(Office.class);
        when(office.getId()).thenReturn(UUID.randomUUID());
        when(office.getFirm()).thenReturn(firm);
        
        Office.Address address = mock(Office.Address.class);
        when(address.getPostcode()).thenReturn("SW1A 1AA");
        when(address.getAddressLine1()).thenReturn("Test Street");
        when(address.getAddressLine2()).thenReturn("");
        when(address.getAddressLine3()).thenReturn("");
        when(address.getCity()).thenReturn("London");
        when(office.getAddress()).thenReturn(address);
        
        return office;
    }
}
