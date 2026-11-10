package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.GetUserProfileByIdQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedApp;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.exception.BadRequestException;
import uk.gov.justice.laa.datauserapi.model.AppType;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.Set;
import java.util.UUID;

public class GetUserProfileByIdQueryMapperTest {

    private GetUserProfileByIdQueryMapper mapper;

    @BeforeEach
    public void setUp() {
        mapper = new GetUserProfileByIdQueryMapper();
    }

    @Test
    public void testMapShouldPopulateQueryForExternalUser() {
        // Given
        UUID profileId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        AuthenticatedEntraUser authenticatedUser = buildAuthenticatedUser(
                UserType.EXTERNAL,
                firmId,
                Set.of()
        );

        // When
        GetUserProfileByIdQuery query = mapper.map(profileId.toString(), authenticatedUser);

        // Then
        Assertions.assertAll(
                () -> Assertions.assertEquals(profileId, query.id()),
                () -> Assertions.assertEquals(firmId, query.firmId()),
                () -> Assertions.assertEquals(UserType.EXTERNAL, query.userType())
        );
    }

    @Test
    public void testMapShouldPopulateQueryForInternalUserWithoutViewExternalPermission() {
        // Given
        UUID profileId = UUID.randomUUID();
        AuthenticatedAppRole roleWithoutExternalViewPermission = buildAuthenticatedAppRole(Set.of(Permission.VIEW_INTERNAL_USER));
        AuthenticatedEntraUser authenticatedUser = buildAuthenticatedUser(
                UserType.INTERNAL,
                UUID.randomUUID(),
                Set.of(roleWithoutExternalViewPermission)
        );

        // When
        GetUserProfileByIdQuery query = mapper.map(profileId.toString(), authenticatedUser);

        // Then
        Assertions.assertAll(
                () -> Assertions.assertEquals(profileId, query.id()),
                () -> Assertions.assertNull(query.firmId()),
                () -> Assertions.assertEquals(UserType.INTERNAL, query.userType())
        );
    }

    @Test
    public void testMapShouldAllowInternalUserWithViewExternalPermissionToQueryWithoutRestrictions() {
        // Given
        UUID profileId = UUID.randomUUID();
        AuthenticatedAppRole roleWithExternalViewPermission = buildAuthenticatedAppRole(Set.of(Permission.VIEW_EXTERNAL_USER));
        AuthenticatedEntraUser authenticatedUser = buildAuthenticatedUser(
                UserType.INTERNAL,
                UUID.randomUUID(),
                Set.of(roleWithExternalViewPermission)
        );

        // When
        GetUserProfileByIdQuery query = mapper.map(profileId.toString(), authenticatedUser);

        // Then
        Assertions.assertAll(
                () -> Assertions.assertEquals(profileId, query.id()),
                () -> Assertions.assertNull(query.firmId()),
                () -> Assertions.assertNull(query.userType())
        );
    }

    @Test
    public void testMapShouldRejectRequestsWithoutAnActiveProfile() {
        // Given
        AuthenticatedEntraUser authenticatedUser = AuthenticatedEntraUser.builder()
                .activeProfile(null)
                .build();

        // When
        BadRequestException exception = Assertions.assertThrows(
                BadRequestException.class,
                () -> mapper.map(UUID.randomUUID().toString(), authenticatedUser)
        );

        // Then
        Assertions.assertEquals("User making request does not have an active user profile or user type", exception.getMessage());
    }

    @Test
    public void testMapShouldRejectInvalidUuid() {
        // Given
        AuthenticatedEntraUser authenticatedUser = buildAuthenticatedUser(
                UserType.EXTERNAL,
                UUID.randomUUID(),
                Set.of()
        );

        // When
        BadRequestException exception = Assertions.assertThrows(
                BadRequestException.class,
                () -> mapper.map("not-a-uuid", authenticatedUser)
        );

        // Then
        Assertions.assertEquals("Provided value 'not-a-uuid' is not a valid UUID", exception.getMessage());
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
                        null,
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

