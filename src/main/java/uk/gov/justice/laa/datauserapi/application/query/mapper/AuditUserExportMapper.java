package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.Firm;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.AuthzRoleType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AuditUserExportMapper {

    private static final String YES = "Yes";
    private static final String NO = "No";

    public List<AuditUserExportRow> toRows(List<UUID> orderedIds, List<UserProfile> profiles) {
        Map<UUID, UserProfile> detailsById = profiles.stream()
                .collect(Collectors.toMap(UserProfile::getId, Function.identity()));

        return orderedIds.stream()
                .map(detailsById::get)
                .filter(Objects::nonNull)
                .map(this::toRow)
                .toList();
    }

    public AuditUserExportRow toRow(UserProfile profile) {
        EntraUser entraUser = profile.getEntraUser();
        Firm firm = profile.getFirm();
        Set<AppRole> appRoles = profile.getAppRoles();

        return new AuditUserExportRow(
                entraUser.getFirstName() + " " + entraUser.getLastName(),
                entraUser.getEmail(),
                firm != null ? firm.getName() : "",
                firm != null ? firm.getCode() : "",
                entraUser.isMultiFirmUser() ? YES : NO,
                isProviderAdmin(appRoles) ? YES : NO,
                distinctAppNames(appRoles),
                entraUser.getUserAccountStatus().getValue(),
                roleNames(appRoles)
        );
    }

    private boolean isProviderAdmin(Set<AppRole> appRoles) {
        return appRoles.stream()
                .filter(AppRole::isAuthzRole)
                .map(AppRole::getName)
                .map(AuthzRole::fromRoleName)
                .flatMap(role -> role.stream())
                .anyMatch(role -> role.getAuthzRoleType() == AuthzRoleType.PROVIDER_ADMIN);
    }

    private String distinctAppNames(Set<AppRole> appRoles) {
        return appRoles.stream()
                .map(AppRole::getApp)
                .map(App::getName)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(", "));
    }

    private String roleNames(Set<AppRole> appRoles) {
        return appRoles.stream()
                .map(AppRole::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(", "));
    }
}
