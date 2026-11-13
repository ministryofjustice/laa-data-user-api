package uk.gov.justice.laa.datauserapi.application.query.mapper;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
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
                toUserTypeRestriction(role.getUserTypeRestriction()).name(),
                toFirmTypeRestrictions(role.getFirmTypeRestriction()));
    }

    /** A role restricted to exactly one user type exposes that type; no restriction or both types is {@code BOTH}. */
    private AppRoleUserType toUserTypeRestriction(UserType[] restrictions) {
        boolean internal = permits(restrictions, UserType.INTERNAL);
        boolean external = permits(restrictions, UserType.EXTERNAL);
        if (internal == external) {
            return AppRoleUserType.BOTH;
        }
        return internal ? AppRoleUserType.INTERNAL : AppRoleUserType.EXTERNAL;
    }

    private boolean permits(UserType[] restrictions, UserType type) {
        return restrictions != null && Arrays.asList(restrictions).contains(type);
    }

    private List<String> toFirmTypeRestrictions(FirmType[] restrictions) {
        if (restrictions == null) {
            return List.of();
        }
        return Arrays.stream(restrictions).map(Enum::name).toList();
    }
}
