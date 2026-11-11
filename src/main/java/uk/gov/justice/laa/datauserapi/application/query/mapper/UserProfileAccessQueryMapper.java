package uk.gov.justice.laa.datauserapi.application.query.mapper;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.model.AppRoleUserType;

/** Maps persistence entities loaded for the user profile access queries to response views. */
@Component
public class UserProfileAccessQueryMapper {

    public List<AppRoleView> toAppRoleViews(List<AppRole> roles) {
        return roles.stream().map(this::toAppRoleView).toList();
    }

    private AppRoleView toAppRoleView(AppRole role) {
        App app = role.getApp();
        return new AppRoleView(
                role.getId().toString(),
                app.getId().toString(),
                app.getName(),
                app.getEntraAppId(),
                role.getName(),
                role.getDescription(),
                role.isAuthzRole(),
                role.getRoleIdentifier(),
                role.isLegacySync(),
                role.getOrdinal(),
                toUserTypeRestriction(role).name(),
                role.getFirmTypeRestriction() == null
                        ? List.of()
                        : Arrays.stream(role.getFirmTypeRestriction()).map(Enum::name).toList());
    }

    private AppRoleUserType toUserTypeRestriction(AppRole role) {
        if (role.getUserTypeRestriction() == null || Arrays.stream(role.getUserTypeRestriction()).distinct().count() != 1) {
            return AppRoleUserType.BOTH;
        }
        return AppRoleUserType.valueOf(role.getUserTypeRestriction()[0].name());
    }
}
