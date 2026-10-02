package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountSummaryView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserView;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.model.AppRoleUserType;
import uk.gov.justice.laa.datauserapi.model.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.model.UserType;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


class UserViewMapperTest {

    private UserViewMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UserViewMapper();
    }

    @Test
    void shouldMapUserView() {

        String entraOid = UUID.randomUUID().toString();

        EntraUser user = EntraUser.builder()
                .entraOid(entraOid)
                .email("test@test.com")
                .firstName("Josh")
                .lastName("Halladey")
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .multiFirmUser(false)
                .mailOnly(false)
                .build();

        Firm firm = Firm.builder()
                .id(UUID.randomUUID())
                .name("Test Firm")
                .build();

        Office.Address address = Office.Address.builder()
                .postcode("SW1")
                .addressLine1("Line1")
                .city("London")
                .build();

        Office office = Office.builder()
                .id(UUID.randomUUID())
                .firm(firm)
                .address(address)
                .build();

        App app = App.builder()
                .id(UUID.randomUUID())
                .name("CCMS")
                .build();

        AppRole role = AppRole.builder()
                .id(UUID.randomUUID())
                .app(app)
                .name("ROLE_USER")
                .description("Role description")
                .userTypeRestriction(new UserType[]{UserType.INTERNAL})
                .build();

        UserProfile profile = UserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .firm(firm)
                .entraUser(user)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .appRoles(Set.of(role))
                .offices(Set.of(office))
                .build();

        EntraUserDto dto = new EntraUserDto();
        dto.setEntraOid(entraOid);
        dto.setEmail("test@test.com");
        dto.setFirstName("Josh");
        dto.setLastName("Halladey");
        dto.setUserAccountStatus(UserAccountStatus.ACTIVE);
        dto.setUserProfiles(Set.of(profile));

        UserView result = mapper.mapToUserView(dto);

        assertNotNull(result);

        UserAccountSummaryView account = result.userAccount();

        assertEquals(entraOid, account.userEntraObjectId());
        assertEquals("test@test.com", account.email());
        assertEquals("Josh", account.firstName());
        assertEquals("Halladey", account.lastName());
        assertEquals(1, account.profileCount());

        assertEquals(
                UUID.fromString(entraOid),
                result.activeProfile().userEntraObjectId());

        assertEquals(1, result.activeProfile().roles().size());
        assertEquals(1, result.activeProfile().offices().size());

        assertEquals(
                AppRoleUserType.INTERNAL,
                result.activeProfile()
                        .roles()
                        .get(0)
                        .appRoleUserTypeRestriction());
    }

    @Test
    void shouldThrowWhenNoActiveProfileFound() {

        UserProfile profile = UserProfile.builder()
                .activeProfile(false)
                .build();

        EntraUserDto dto = new EntraUserDto();
        dto.setEntraOid(UUID.randomUUID().toString());
        dto.setUserProfiles(Set.of(profile));

        assertThrows(
                InvalidActorContextException.class,
                () -> mapper.mapToUserView(dto));
    }

    @Test
    void shouldMapSummaryView() {

        UserProfile profile1 = UserProfile.builder().build();
        UserProfile profile2 = UserProfile.builder().build();

        EntraUserDto dto = new EntraUserDto();
        dto.setEntraOid("oid");
        dto.setEmail("user@test.com");
        dto.setFirstName("Josh");
        dto.setLastName("Halladey");
        dto.setUserAccountStatus(UserAccountStatus.ACTIVE);
        dto.setUserProfiles(Set.of(profile1, profile2));

        UserAccountSummaryView result =
                mapper.mapToUserAccountSummaryView(dto);

        assertEquals("oid", result.userEntraObjectId());
        assertEquals("user@test.com", result.email());
        assertEquals("Josh", result.firstName());
        assertEquals("Halladey", result.lastName());
        assertEquals(2, result.profileCount());
    }

    @Test
    void shouldMapRoleRestrictionToBothWhenNull() {

        AppRole role = AppRole.builder()
                .id(UUID.randomUUID())
                .app(App.builder()
                        .id(UUID.randomUUID())
                        .name("CCMS")
                        .build())
                .name("ROLE")
                .userTypeRestriction(null)
                .build();

        UserProfile profile = buildProfile(role);

        UserView result = mapper.mapToUserView(buildDto(profile));

        assertEquals(
                AppRoleUserType.BOTH,
                result.activeProfile()
                        .roles()
                        .get(0)
                        .appRoleUserTypeRestriction());
    }

    @Test
    void shouldMapRoleRestrictionToExternal() {

        AppRole role = AppRole.builder()
                .id(UUID.randomUUID())
                .app(App.builder()
                        .id(UUID.randomUUID())
                        .name("CCMS")
                        .build())
                .name("ROLE")
                .userTypeRestriction(new UserType[]{UserType.EXTERNAL})
                .build();

        UserProfile profile = buildProfile(role);

        UserView result = mapper.mapToUserView(buildDto(profile));

        assertEquals(
                AppRoleUserType.EXTERNAL,
                result.activeProfile()
                        .roles()
                        .get(0)
                        .appRoleUserTypeRestriction());
    }

    private UserProfile buildProfile(AppRole role) {

        String oid = UUID.randomUUID().toString();

        EntraUser user = EntraUser.builder()
                .entraOid(oid)
                .email("test@test.com")
                .firstName("Test")
                .lastName("User")
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .build();

        Firm firm = Firm.builder()
                .id(UUID.randomUUID())
                .name("Firm")
                .build();

        return UserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .firm(firm)
                .entraUser(user)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .appRoles(Set.of(role))
                .offices(Set.of())
                .build();
    }

    private EntraUserDto buildDto(UserProfile profile) {

        EntraUserDto dto = new EntraUserDto();

        dto.setEntraOid(profile.getEntraUser().getEntraOid());
        dto.setEmail(profile.getEntraUser().getEmail());
        dto.setFirstName(profile.getEntraUser().getFirstName());
        dto.setLastName(profile.getEntraUser().getLastName());
        dto.setUserAccountStatus(UserAccountStatus.ACTIVE);
        dto.setUserProfiles(Set.of(profile));

        return dto;
    }
}