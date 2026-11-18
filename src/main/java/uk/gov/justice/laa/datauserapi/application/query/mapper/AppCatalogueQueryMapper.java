package uk.gov.justice.laa.datauserapi.application.query.mapper;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.model.AppRoleUserType;

/**
 * Maps app roles loaded for the catalogue queries to response views. The role view mapping, including the
 * INTERNAL/EXTERNAL/BOTH rule, is the one shared with the user profile access queries.
 */
@Component
public class AppCatalogueQueryMapper {

    private final UserProfileAccessQueryMapper roleViewMapper;

    public AppCatalogueQueryMapper(UserProfileAccessQueryMapper roleViewMapper) {
        this.roleViewMapper = roleViewMapper;
    }

    /** Keeps the roles available to the user type: those restricted to it and {@code BOTH}; all if {@code null}. */
    public List<AppRoleView> toAppRoleViews(List<AppRole> roles, UserType userType) {
        return roleViewMapper.toAppRoleViews(roles).stream()
                .filter(view -> isAvailableTo(view, userType))
                .toList();
    }

    public Optional<AppRoleView> toAppRoleView(Optional<AppRole> role) {
        return role.map(found -> roleViewMapper.toAppRoleViews(List.of(found)).getFirst());
    }

    private boolean isAvailableTo(AppRoleView view, UserType userType) {
        return userType == null
                || AppRoleUserType.BOTH.name().equals(view.appRoleUserTypeRestriction())
                || userType.name().equals(view.appRoleUserTypeRestriction());
    }
}
