package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchAccess;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;

class FirmAuthorisationServiceTest {

    private FirmAuthorisationService authorisationService;

    @BeforeEach
    void setUp() {
        authorisationService = new FirmAuthorisationService();
    }

    @Test
    void requireFirmDirectoryAccess_throws_whenUserIsExternal() {
        assertThatThrownBy(() -> authorisationService.requireFirmDirectoryAccess(user(UserType.EXTERNAL, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("External users cannot access the firm directory");
    }

    @Test
    void requireFirmDirectoryAccess_throws_whenInternalUserLacksPermission() {
        assertThatThrownBy(() -> authorisationService.requireFirmDirectoryAccess(user(UserType.INTERNAL, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Missing VIEW_FIRM_DIRECTORY permission");
    }

    @Test
    void requireFirmDirectoryAccess_succeeds_whenInternalUserHasPermission() {
        authorisationService.requireFirmDirectoryAccess(
                user(UserType.INTERNAL, null, Permission.VIEW_FIRM_DIRECTORY));
    }

    @Test
    void requireFirmDirectoryAccess_throws_whenUserHasNoActiveProfile() {
        assertThatThrownBy(() -> authorisationService.requireFirmDirectoryAccess(
                AuthenticatedEntraUser.builder().build()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("No active user profile found");
    }

    @Test
    void resolveSearchAccess_allowsAllFirms_forInternalUser() {
        FirmSearchAccess access = authorisationService.resolveSearchAccess(user(UserType.INTERNAL, null));

        assertThat(access).isEqualTo(new FirmSearchAccess(true, null));
    }

    @Test
    void resolveSearchAccess_restrictsToActiveFirm_forExternalUser() {
        FirmSearchAccess access = authorisationService.resolveSearchAccess(user(UserType.EXTERNAL, "123456"));

        assertThat(access).isEqualTo(new FirmSearchAccess(false, "123456"));
    }

    @Test
    void resolveSearchAccess_deniesExternalUser_whenNoActiveFirm() {
        assertThatThrownBy(() -> authorisationService.resolveSearchAccess(user(UserType.EXTERNAL, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("External user has no active firm");
    }

    private AuthenticatedEntraUser user(UserType userType, String firmCode, Permission... permissions) {
        Set<Permission> grantedPermissions = Set.of(permissions);
        Set<AuthenticatedAppRole> roles = grantedPermissions.isEmpty()
                ? Set.of()
                : Set.of(new AuthenticatedAppRole(
                        null, null, 0, null, false, null, null, null, false, null, grantedPermissions));
        AuthenticatedUserProfile profile = new AuthenticatedUserProfile(
                null, true, userType, null, null, null, firmCode, null, null, false, false, Set.of(), roles);
        return AuthenticatedEntraUser.builder().activeProfile(profile).build();
    }
}
