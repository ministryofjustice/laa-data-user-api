package uk.gov.justice.laa.datauserapi.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.dto.UserDto;
import uk.gov.justice.laa.datauserapi.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.entity.*;
import uk.gov.justice.laa.datauserapi.repository.*;
import uk.gov.justice.laa.datauserapi.service.UserService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class UserServiceImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private EntraUserRepository entraUserRepository;

    @Autowired
    private FirmRepository firmRepository;

    @Autowired
    private OfficeRepository officeRepository;

    @Autowired
    private AppRepository appRepository;

    @Autowired
    private AppRoleRepository appRoleRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    private UserProfile testProfile1;
    private UserProfile testProfile2;
    private Firm testFirm;
    private Office testOffice;
    private AppRole testRole;

    @BeforeEach
    void setUp() {
        // Setup Entra Users
        EntraUser entraUser1 = EntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .email("test1@example.com")
                .firstName("Test")
                .lastName("One")
                .userStatus(UserStatus.ACTIVE)
                .createdBy("system")
                .createdDate(java.time.LocalDateTime.now())
                .build();
        entraUser1 = entraUserRepository.saveAndFlush(entraUser1);

        EntraUser entraUser2 = EntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .email("test2@example.com")
                .firstName("Test")
                .lastName("Two")
                .userStatus(UserStatus.ACTIVE)
                .createdBy("system")
                .createdDate(java.time.LocalDateTime.now())
                .build();
        entraUser2 = entraUserRepository.saveAndFlush(entraUser2);

        // Setup Firm & Office
        testFirm = Firm.builder()
                .name("Integration Test Firm")
                .enabled(true)
                .type(FirmType.LEGAL_SERVICES_PROVIDER)
                .build();
        testFirm = firmRepository.saveAndFlush(testFirm);

        testOffice = Office.builder()
                .code("INT-OFFICE-01")
                .firm(testFirm)
                .build();
        testOffice = officeRepository.saveAndFlush(testOffice);

        // Setup App & Role
        App testApp = App.builder()
                .name("TestApp")
                .description("Test App Description")
                .appType(AppType.LAA)
                .url("http://test.app")
                .securityGroupOid(UUID.randomUUID().toString())
                .build();
        testApp = appRepository.saveAndFlush(testApp);

        testRole = AppRole.builder()
                .name("TestRole")
                .description("Test Role Description")
                .app(testApp)
                .build();
        testRole = appRoleRepository.saveAndFlush(testRole);

        // Setup User Profiles
        testProfile1 = UserProfile.builder()
                .entraUser(entraUser1)
                .firm(testFirm)
                .offices(Set.of(testOffice))
                .appRoles(Set.of(testRole))
                .userType(UserType.EXTERNAL)
                .userProfileStatus(UserProfileStatus.COMPLETE)
                .silasStatus(UserProfileSilasStatus.COMPLETE)
                .createdBy("system")
                .createdDate(java.time.LocalDateTime.now())
                .build();
        testProfile1 = userProfileRepository.saveAndFlush(testProfile1);

        testProfile2 = UserProfile.builder()
                .entraUser(entraUser2)
                .userType(UserType.INTERNAL)
                .userProfileStatus(UserProfileStatus.PENDING)
                .silasStatus(UserProfileSilasStatus.DISABLED)
                .createdBy("system")
                .createdDate(java.time.LocalDateTime.now())
                .build();
        testProfile2 = userProfileRepository.saveAndFlush(testProfile2);
    }

    @Test
    void findUsers_withNoCriteria_shouldReturnAll() {
        UserSearchCriteria criteria = new UserSearchCriteria(null, null, null, null, null, null, null, null);
        Page<UserDto> result = userService.findUsers(criteria, PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void findUsers_byEmail_shouldReturnSpecificUser() {
        UserSearchCriteria criteria = new UserSearchCriteria(
                null, null, List.of("test1@example.com"), null, null, null, null, null);
        Page<UserDto> result = userService.findUsers(criteria, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("test1@example.com");
        assertThat(result.getContent().get(0).firmName()).isEqualTo("Integration Test Firm");
    }

    @Test
    void findUsers_byFirmId_shouldReturnUsersInFirm() {
        UserSearchCriteria criteria = new UserSearchCriteria(
                null, null, null, null, null, null, null, List.of(testFirm.getId().toString()));
        Page<UserDto> result = userService.findUsers(criteria, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("test1@example.com");
    }

    @Test
    void findUsers_byRoleName_shouldReturnUsersWithRole() {
        UserSearchCriteria criteria = new UserSearchCriteria(
                null, null, null, null, List.of("TestRole"), null, null, null);
        Page<UserDto> result = userService.findUsers(criteria, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("test1@example.com");
    }

    @Test
    void findUsers_byOfficeId_shouldReturnUsersInOffice() {
        UserSearchCriteria criteria = new UserSearchCriteria(
                null, null, null, null, null, null, List.of(testOffice.getId().toString()), null);
        Page<UserDto> result = userService.findUsers(criteria, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("test1@example.com");
    }

    @Test
    void getUserById_shouldReturnUserDetails() {
        java.util.Optional<uk.gov.justice.laa.datauserapi.dto.UserDetailDto> result = userService.getUserById(testProfile1.getId());

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo("test1@example.com");
        assertThat(result.get().firmName()).isEqualTo("Integration Test Firm");
        assertThat(result.get().roleNames()).containsExactly("TestRole");
        assertThat(result.get().officeCodes()).containsExactly("INT-OFFICE-01");
    }
}
