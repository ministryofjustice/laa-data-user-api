package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.dto.AppDto;
import uk.gov.justice.laa.datauserapi.dto.AppRoleDto;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.FirmDto;
import uk.gov.justice.laa.datauserapi.dto.OfficeDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.model.AppRoleUserType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.List;
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

        EntraUserDto user = EntraUserDto.builder()
                .entraOid(entraOid)
                .email("test@test.com")
                .firstName("Josh")
                .lastName("Halladey")
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .multiFirmUser(false)
                .mailOnly(false)
                .build();

        FirmDto firm = FirmDto.builder()
                .id(UUID.randomUUID())
                .name("Test Firm")
                .build();

        OfficeDto.AddressDto address = OfficeDto.AddressDto.builder()
                .postcode("SW1")
                .addressLine1("Line1")
                .city("London")
                .build();

        OfficeDto office = OfficeDto.builder()
                .id(UUID.randomUUID())
                .firmId(firm.getId().toString())
                .address(address)
                .build();

        AppDto app = AppDto.builder()
                .id(UUID.randomUUID().toString())
                .name("CCMS")
                .build();

        AppRoleDto role = AppRoleDto.builder()
                .id(UUID.randomUUID().toString())
                .app(app)
                .name("ROLE_USER")
                .description("Role description")
                .userTypeRestriction(new UserType[]{UserType.INTERNAL})
                .build();

        UserProfileDto profile = UserProfileDto.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .firm(firm)
                .entraUser(user)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .appRoles(List.of(role))
                .offices(List.of(office))
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
                String.valueOf(AppRoleUserType.INTERNAL),
                result.activeProfile()
                        .roles()
                        .getFirst()
                        .appRoleUserTypeRestriction());
    }

    @Test
    void shouldThrowWhenNoActiveProfileFound() {

        UserProfileDto profile = UserProfileDto.builder()
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

        UserProfileDto profile1 = UserProfileDto.builder().id(UUID.randomUUID()).build();
        UserProfileDto profile2 = UserProfileDto.builder().id(UUID.randomUUID()).build();

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

        AppRoleDto role = AppRoleDto.builder()
                .id(UUID.randomUUID().toString())
                .app(AppDto.builder()
                        .id(UUID.randomUUID().toString())
                        .name("CCMS")
                        .build())
                .name("ROLE")
                .userTypeRestriction(null)
                .build();

        UserProfileDto profile = buildProfile(role);

        UserView result = mapper.mapToUserView(buildDto(profile));

        assertEquals(
                String.valueOf(AppRoleUserType.BOTH),
                result.activeProfile()
                        .roles()
                        .getFirst()
                        .appRoleUserTypeRestriction());
    }

    @Test
    void shouldMapRoleRestrictionToExternal() {

        AppRoleDto role = AppRoleDto.builder()
                .id(UUID.randomUUID().toString())
                .app(AppDto.builder()
                        .id(UUID.randomUUID().toString())
                        .name("CCMS")
                        .build())
                .name("ROLE")
                .userTypeRestriction(new UserType[]{UserType.EXTERNAL})
                .build();

        UserProfileDto profile = buildProfile(role);

        UserView result = mapper.mapToUserView(buildDto(profile));

        assertEquals(
                String.valueOf(AppRoleUserType.EXTERNAL),
                result.activeProfile()
                        .roles()
                        .getFirst()
                        .appRoleUserTypeRestriction());
    }

    private UserProfileDto buildProfile(AppRoleDto role) {

        String oid = UUID.randomUUID().toString();

        EntraUserDto user = EntraUserDto.builder()
                .entraOid(oid)
                .email("test@test.com")
                .firstName("Test")
                .lastName("User")
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .build();

        FirmDto firm = FirmDto.builder()
                .id(UUID.randomUUID())
                .name("Firm")
                .build();

        return UserProfileDto.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(UserType.INTERNAL)
                .firm(firm)
                .entraUser(user)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .appRoles(List.of(role))
                .offices(List.of())
                .build();
    }

    private EntraUserDto buildDto(UserProfileDto profile) {

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
