package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.request.GetUserProfilesRequest;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedApp;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.exception.BadRequestException;
import uk.gov.justice.laa.datauserapi.model.AppType;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.Set;
import java.util.UUID;

public class GetUserProfilesQueryMapperTest {

    private GetUserProfilesQueryMapper mapper;
    
    @BeforeEach
    public void setUp() {
        mapper = new GetUserProfilesQueryMapper();
    }

    @Test
    public void testMapShouldPopulateQueryAndDeriveExternalAuthContextFromFirmUuid() {
        // Given
        UUID appRoleId = UUID.randomUUID();
        UUID firmUuid = UUID.randomUUID();
        UUID authFirmId = UUID.randomUUID();
        GetUserProfilesRequest request = buildRequest(
                2,
                25,
                new String[]{"lastName,desc", "firstName,asc"},
                UserType.EXTERNAL.name(),
                UserProfileStatus.COMPLETE.name(),
                "app-id-external",
                appRoleId.toString(),
                "office-id-external",
                null,
                "user-external",
                firmUuid.toString(),
                Boolean.TRUE,
                Boolean.FALSE
        );
        AuthenticatedEntraUser authenticatedUser = buildAuthenticatedUser(
                UserType.EXTERNAL,
                authFirmId,
                Set.of()
        );

        // When
        GetUserProfilesQuery query = mapper.map(request, authenticatedUser);

        // Then
        Assertions.assertAll(
                () -> Assertions.assertEquals(UserType.EXTERNAL, query.userType()),
                () -> Assertions.assertEquals(UserProfileStatus.COMPLETE, query.userProfileStatus()),
                () -> Assertions.assertEquals("app-id-external", query.appId()),
                () -> Assertions.assertEquals(appRoleId, query.appRoleId()),
                () -> Assertions.assertEquals("office-id-external", query.officeId()),
                () -> Assertions.assertEquals(firmUuid, query.firmId()),
                () -> Assertions.assertEquals("user-external", query.user()),
                () -> Assertions.assertEquals(firmUuid.toString(), query.firm()),
                () -> Assertions.assertEquals(Boolean.TRUE, query.firmAdmin()),
                () -> Assertions.assertEquals(Boolean.FALSE, query.multiFirmUser()),
                () -> Assertions.assertEquals(PageRequest.of(2, 25, Sort.by(
                        Sort.Order.desc("lastName"),
                        Sort.Order.asc("firstName")
                )), query.pageRequest()),
                () -> Assertions.assertEquals(authFirmId, query.authUserFirmId()),
                () -> Assertions.assertEquals(UserType.EXTERNAL, query.authUserType())
        );
    }

    @Test
    public void testMapShouldPopulateQueryAndNotPopulateFirmIdIfFirmSearchIsNotId() {
        // Given
        UUID appRoleId = UUID.randomUUID();
        UUID authFirmId = UUID.randomUUID();
        GetUserProfilesRequest request = buildRequest(
                2,
                25,
                new String[]{"lastName,desc", "firstName,asc"},
                UserType.EXTERNAL.name(),
                UserProfileStatus.COMPLETE.name(),
                "app-id-external",
                appRoleId.toString(),
                "office-id-external",
                null,
                "user-external",
                "not-a-uuid",
                Boolean.TRUE,
                Boolean.FALSE
        );
        AuthenticatedEntraUser authenticatedUser = buildAuthenticatedUser(
                UserType.EXTERNAL,
                authFirmId,
                Set.of()
        );

        // When
        GetUserProfilesQuery query = mapper.map(request, authenticatedUser);

        // Then
        Assertions.assertAll(
                () -> Assertions.assertEquals(UserType.EXTERNAL, query.userType()),
                () -> Assertions.assertEquals(UserProfileStatus.COMPLETE, query.userProfileStatus()),
                () -> Assertions.assertEquals("app-id-external", query.appId()),
                () -> Assertions.assertEquals(appRoleId, query.appRoleId()),
                () -> Assertions.assertEquals("office-id-external", query.officeId()),
                () -> Assertions.assertNull(query.firmId()),
                () -> Assertions.assertEquals("user-external", query.user()),
                () -> Assertions.assertEquals("not-a-uuid", query.firm()),
                () -> Assertions.assertEquals(Boolean.TRUE, query.firmAdmin()),
                () -> Assertions.assertEquals(Boolean.FALSE, query.multiFirmUser()),
                () -> Assertions.assertEquals(PageRequest.of(2, 25, Sort.by(
                        Sort.Order.desc("lastName"),
                        Sort.Order.asc("firstName")
                )), query.pageRequest()),
                () -> Assertions.assertEquals(authFirmId, query.authUserFirmId()),
                () -> Assertions.assertEquals(UserType.EXTERNAL, query.authUserType())
        );
    }

    @Test
    public void testMapShouldKeepExplicitFirmIdAndLeaveInternalAuthContextUnsetWhenPermissionIsPresent() {
        // Given
        UUID explicitFirmId = UUID.randomUUID();
        AuthenticatedAppRole appRoleWithPermission = buildAuthenticatedAppRole(Set.of(Permission.VIEW_EXTERNAL_USER));
        GetUserProfilesRequest request = buildRequest(
                0,
                10,
                null,
                UserType.INTERNAL.name(),
                UserProfileStatus.COMPLETE.name(),
                "app-id-internal-permission",
                null,
                "office-id-internal-permission",
                explicitFirmId.toString(),
                "user-internal-permission",
                null,
                Boolean.FALSE,
                Boolean.TRUE
        );
        AuthenticatedEntraUser authenticatedUser = buildAuthenticatedUser(
                UserType.INTERNAL,
                UUID.randomUUID(),
                Set.of(appRoleWithPermission)
        );

        // When
        GetUserProfilesQuery query = mapper.map(request, authenticatedUser);

        // Then
        Assertions.assertAll(
                () -> Assertions.assertEquals(explicitFirmId, query.firmId()),
                () -> Assertions.assertNull(query.firm()),
                () -> Assertions.assertNull(query.authUserFirmId()),
                () -> Assertions.assertNull(query.authUserType()),
                () -> Assertions.assertEquals(PageRequest.of(0, 10), query.pageRequest())
        );
    }

    @Test
    public void testMapShouldNotOverwriteExplicitFirmIdWhenFirmFieldContainsUuid() {
        // Given
        UUID explicitFirmId = UUID.randomUUID();
        UUID requestFirmUuid = UUID.randomUUID();
        AuthenticatedAppRole appRoleWithPermission = buildAuthenticatedAppRole(Set.of(Permission.VIEW_EXTERNAL_USER));
        GetUserProfilesRequest request = buildRequest(
                3,
                15,
                null,
                UserType.INTERNAL.name(),
                UserProfileStatus.COMPLETE.name(),
                "app-id-firm-precedence",
                null,
                "office-id-firm-precedence",
                explicitFirmId.toString(),
                "user-firm-precedence",
                requestFirmUuid.toString(),
                null,
                null
        );
        AuthenticatedEntraUser authenticatedUser = buildAuthenticatedUser(
                UserType.INTERNAL,
                UUID.randomUUID(),
                Set.of(appRoleWithPermission)
        );

        // When
        GetUserProfilesQuery query = mapper.map(request, authenticatedUser);

        // Then
        Assertions.assertAll(
                () -> Assertions.assertEquals(explicitFirmId, query.firmId()),
                () -> Assertions.assertEquals(requestFirmUuid.toString(), query.firm()),
                () -> Assertions.assertNull(query.authUserFirmId()),
                () -> Assertions.assertNull(query.authUserType()),
                () -> Assertions.assertEquals(PageRequest.of(3, 15), query.pageRequest())
        );
    }

    @Test
    public void testMapShouldDeriveInternalAuthContextWithoutViewExternalPermissionAndIgnoreNonUuidFirmField() {
        // Given
        UUID requestFirmId = UUID.randomUUID();
        AuthenticatedAppRole appRoleWithoutPermission = buildAuthenticatedAppRole(Set.of(Permission.VIEW_INTERNAL_USER));
        GetUserProfilesRequest request = buildRequest(
                4,
                50,
                new String[]{"user,asc"},
                null,
                null,
                "app-id-internal-no-permission",
                null,
                "office-id-internal-no-permission",
                requestFirmId.toString(),
                "user-internal-no-permission",
                "not-a-uuid-firm",
                null,
                null
        );
        AuthenticatedEntraUser authenticatedUser = buildAuthenticatedUser(
                UserType.INTERNAL,
                UUID.randomUUID(),
                Set.of(appRoleWithoutPermission)
        );

        // When
        GetUserProfilesQuery query = mapper.map(request, authenticatedUser);

        // Then
        Assertions.assertAll(
                () -> Assertions.assertNull(query.userType()),
                () -> Assertions.assertNull(query.userProfileStatus()),
                () -> Assertions.assertEquals(requestFirmId, query.firmId()),
                () -> Assertions.assertEquals("not-a-uuid-firm", query.firm()),
                () -> Assertions.assertEquals(UserType.INTERNAL, query.authUserType()),
                () -> Assertions.assertNull(query.authUserFirmId()),
                () -> Assertions.assertEquals(PageRequest.of(4, 50, Sort.by(Sort.Order.asc("user"))), query.pageRequest())
        );
    }

    @Test
    public void testMapShouldRejectRequestsFromUsersWithoutAnActiveProfile() {
        // Given
        GetUserProfilesRequest request = buildRequest(
                1,
                5,
                null,
                UserType.INTERNAL.name(),
                UserProfileStatus.COMPLETE.name(),
                "app-id-missing-profile",
                null,
                "office-id-missing-profile",
                null,
                "user-missing-profile",
                null,
                null,
                null
        );
        AuthenticatedEntraUser authenticatedUser = AuthenticatedEntraUser.builder()
                .activeProfile(null)
                .build();

        // When
        BadRequestException exception = Assertions.assertThrows(
                BadRequestException.class,
                () -> mapper.map(request, authenticatedUser)
        );

        // Then
        Assertions.assertEquals("User making request does not have an active user profile or user type", exception.getMessage());
    }

    private static GetUserProfilesRequest buildRequest(Integer pageNumber,
                                                       Integer pageSize,
                                                       String[] sortBy,
                                                       String userType,
                                                       String userProfileStatus,
                                                       String appId,
                                                       String appRoleId,
                                                       String officeId,
                                                       String firmId,
                                                       String user,
                                                       String firm,
                                                       Boolean firmAdmin,
                                                       Boolean multiFirmUser) {
        return GetUserProfilesRequest.builder()
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .sortBy(sortBy)
                .userType(userType)
                .userProfileStatus(userProfileStatus)
                .appId(appId)
                .appRoleId(appRoleId)
                .officeId(officeId)
                .firmId(firmId)
                .user(user)
                .firm(firm)
                .firmAdmin(firmAdmin)
                .multiFirmUser(multiFirmUser)
                .build();
    }

    private static AuthenticatedEntraUser buildAuthenticatedUser(UserType userType,
                                                                  UUID firmId,
                                                                  Set<AuthenticatedAppRole> appRoles) {
        return AuthenticatedEntraUser.builder()
                .activeProfile(new AuthenticatedUserProfile(
                        UUID.randomUUID(),
                        true,
                        userType,
                        UUID.randomUUID(),
                        firmId,
                        "Test Firm",
                        "TEST",
                        UserProfileStatus.COMPLETE,
                        null,
                        true,
                        false,
                        Set.of(),
                        appRoles
                ))
                .build();
    }

    private static AuthenticatedAppRole buildAuthenticatedAppRole(Set<Permission> permissions) {
        return new AuthenticatedAppRole(
                UUID.randomUUID(),
                "Test Role",
                1,
                "CCMS-CODE",
                false,
                new FirmType[0],
                new UserType[0],
                "Test role description",
                false,
                buildAuthenticatedApp(),
                permissions
        );
    }

    private static AuthenticatedApp buildAuthenticatedApp() {
        return new AuthenticatedApp(
                UUID.randomUUID(),
                "Test App",
                "Test app description",
                AppType.LAA,
                1,
                "https://example.test",
                true,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
        );
    }
}
