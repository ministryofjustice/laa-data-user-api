package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsSyncProfile;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CcmsSyncProfileMapperTest {

    private final CcmsSyncProfileMapper mapper = new CcmsSyncProfileMapper();

    @Test
    void mapsIdentityAndGroupsOnlyLegacyRolesWithoutChangingEntityRoles() {
        UserProfile profile = validProfile();
        AppRole first = role("application-one", "role-one", true);
        AppRole second = role("application-one", "role-two", true);
        AppRole third = role("application-two", "role-three", true);
        AppRole nonLegacy = role(null, null, false);
        nonLegacy.setApp(null);
        Set<AppRole> originalRoles = new HashSet<>(Set.of(first, second, third, nonLegacy));
        profile.setAppRoles(originalRoles);

        CcmsSyncProfile snapshot = mapper.toSnapshot(profile);

        assertThat(snapshot.id()).isEqualTo(profile.getId());
        assertThat(snapshot.entraOid()).isEqualTo("user-oid");
        assertThat(snapshot.legacyUserId()).isEqualTo(profile.getLegacyUserId());
        assertThat(snapshot.firstName()).isEqualTo("First");
        assertThat(snapshot.lastName()).isEqualTo("Last");
        assertThat(snapshot.email()).isEqualTo("user@example.test");
        assertThat(snapshot.userType()).isEqualTo(UserType.INTERNAL);
        assertThat(snapshot.rolesByApplication()).isEqualTo(Map.of(
                "application-one", Set.of("role-one", "role-two"),
                "application-two", Set.of("role-three")));
        assertThat(profile.getAppRoles()).isSameAs(originalRoles)
                .containsExactlyInAnyOrder(first, second, third, nonLegacy);
    }

    @Test
    void omitsVendorNumberForInternalUserEvenWhenFirmIsPresent() {
        UserProfile profile = validProfile();
        profile.setFirm(Firm.builder().code("vendor-number").build());

        assertThat(mapper.toSnapshot(profile).vendorNumber()).isNull();
    }

    @Test
    void includesVendorNumberForExternalUser() {
        UserProfile profile = validProfile();
        profile.setUserType(UserType.EXTERNAL);
        profile.setFirm(Firm.builder().code("vendor-number").build());

        assertThat(mapper.toSnapshot(profile).vendorNumber()).isEqualTo("vendor-number");
    }

    @ParameterizedTest
    @MethodSource("invalidProfiles")
    void rejectsProfilesMissingRequiredData(UserProfile profile) {
        assertInvalid(profile);
    }

    private static Stream<UserProfile> invalidProfiles() {
        return Stream.of(
                invalidProfile(profile -> profile.setEntraUser(null)),
                invalidProfile(profile -> profile.setLegacyUserId(null)),
                invalidProfile(profile -> profile.setUserType(null)),
                invalidProfile(profile -> profile.setAppRoles(null)),
                invalidProfile(profile -> profile.getAppRoles().add(null)),
                invalidProfile(profile -> profile.getAppRoles().iterator().next().setApp(null)),
                invalidProfile(profile -> profile.setUserType(UserType.EXTERNAL)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankUserEntraOid(String entraOid) {
        UserProfile profile = validProfile();
        profile.getEntraUser().setEntraOid(entraOid);

        assertInvalid(profile);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankLegacyApplicationOid(String entraOid) {
        UserProfile profile = validProfile();
        profile.getAppRoles().iterator().next().getApp().setEntraOid(entraOid);

        assertInvalid(profile);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankLegacyRoleIdentifier(String identifier) {
        UserProfile profile = validProfile();
        profile.getAppRoles().iterator().next().setRoleIdentifier(identifier);

        assertInvalid(profile);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankExternalVendorNumber(String vendorNumber) {
        UserProfile profile = validProfile();
        profile.setUserType(UserType.EXTERNAL);
        profile.setFirm(Firm.builder().code(vendorNumber).build());

        assertInvalid(profile);
    }

    @Test
    void rejectsEmptyRolesWithNoSyncRolesReason() {
        UserProfile profile = validProfile();
        profile.getAppRoles().clear();

        assertNoSyncRoles(profile);
    }

    @Test
    void rejectsProfilesWithOnlyNonLegacyRolesWithNoSyncRolesReason() {
        UserProfile profile = validProfile();
        profile.getAppRoles().iterator().next().setLegacySync(false);

        assertNoSyncRoles(profile);
    }

    @Test
    void snapshotDoesNotChangeWhenEntityRolesAreMutated() {
        UserProfile profile = validProfile();
        AppRole originalRole = profile.getAppRoles().iterator().next();

        final CcmsSyncProfile snapshot = mapper.toSnapshot(profile);
        originalRole.setRoleIdentifier("changed-role");
        originalRole.getApp().setEntraOid("changed-application");
        profile.getAppRoles().clear();

        assertThat(snapshot.rolesByApplication()).isEqualTo(Map.of("application", Set.of("role")));
    }

    @Test
    void snapshotCopiesAndProtectsBothRoleMapAndNestedSets() {
        Set<String> mutableRoles = new HashSet<>(Set.of("role"));
        Map<String, Set<String>> mutableApplications = new HashMap<>();
        mutableApplications.put("application", mutableRoles);
        CcmsSyncProfile snapshot = new CcmsSyncProfile(UUID.randomUUID(), "user-oid", UUID.randomUUID(),
                "First", "Last", "user@example.test", UserType.INTERNAL, null, mutableApplications);

        mutableRoles.add("added-after-snapshot");
        mutableApplications.clear();

        assertThat(snapshot.rolesByApplication()).isEqualTo(Map.of("application", Set.of("role")));
        assertThatThrownBy(() -> snapshot.rolesByApplication().put("another", Set.of("role")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.rolesByApplication().get("application").add("another"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertInvalid(UserProfile profile) {
        assertThatThrownBy(() -> mapper.toSnapshot(profile))
                .isInstanceOfSatisfying(CcmsSyncException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(CcmsSyncException.Reason.INVALID_PROFILE));
    }

    private void assertNoSyncRoles(UserProfile profile) {
        assertThatThrownBy(() -> mapper.toSnapshot(profile))
                .isInstanceOfSatisfying(CcmsSyncException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(CcmsSyncException.Reason.NO_SYNC_ROLES));
    }

    private static UserProfile invalidProfile(Consumer<UserProfile> mutation) {
        UserProfile profile = validProfile();
        mutation.accept(profile);
        return profile;
    }

    private static UserProfile validProfile() {
        return UserProfile.builder()
                .id(UUID.randomUUID())
                .legacyUserId(UUID.randomUUID())
                .userType(UserType.INTERNAL)
                .entraUser(EntraUser.builder().entraOid("user-oid").firstName("First").lastName("Last")
                        .email("user@example.test").build())
                .appRoles(new HashSet<>(Set.of(role("application", "role", true))))
                .build();
    }

    private static AppRole role(String applicationOid, String identifier, boolean legacySync) {
        return AppRole.builder().id(UUID.randomUUID())
                .app(App.builder().id(UUID.randomUUID()).entraOid(applicationOid).build())
                .roleIdentifier(identifier).legacySync(legacySync).build();
    }
}
