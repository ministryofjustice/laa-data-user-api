package uk.gov.justice.laa.datauserapi.application.query.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;

class UserProfileAccessQueryMapperTest {

    private final UserProfileAccessQueryMapper mapper = new UserProfileAccessQueryMapper();

    @Test
    void mapsRoleWithInternalIdAndEntraClientIdOfItsApp() {
        UUID appId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        App app = App.builder().id(appId).name("App").entraAppId("client-id").build();
        AppRole role = AppRole.builder().id(roleId).name("Role").description("Desc").roleIdentifier("ROLE")
                .ordinal(3).authzRole(true).app(app)
                .userTypeRestriction(new UserType[] {UserType.EXTERNAL})
                .firmTypeRestriction(new FirmType[] {FirmType.CHAMBERS}).build();

        AppRoleView view = mapper.toAppRoleViews(List.of(role)).getFirst();

        assertThat(view.appRoleId()).isEqualTo(roleId.toString());
        assertThat(view.appId()).isEqualTo(appId.toString());
        assertThat(view.appEntraClientId()).isEqualTo("client-id");
        assertThat(view.appName()).isEqualTo("App");
        assertThat(view.authzRole()).isTrue();
        assertThat(view.appRoleUserTypeRestriction()).isEqualTo("EXTERNAL");
        assertThat(view.firmTypeRestriction()).containsExactly("CHAMBERS");
    }

    @Test
    void unrestrictedRolesAreReportedAsBoth() {
        App app = App.builder().id(UUID.randomUUID()).name("App").build();
        AppRole role = AppRole.builder().id(UUID.randomUUID()).name("Role").app(app).build();

        AppRoleView view = mapper.toAppRoleViews(List.of(role)).getFirst();

        assertThat(view.appRoleUserTypeRestriction()).isEqualTo("BOTH");
        assertThat(view.firmTypeRestriction()).isEmpty();
    }
}
