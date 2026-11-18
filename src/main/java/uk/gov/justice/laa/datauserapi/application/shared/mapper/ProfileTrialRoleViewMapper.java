package uk.gov.justice.laa.datauserapi.application.shared.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import java.util.Arrays;
import java.util.List;

@Component
public class ProfileTrialRoleViewMapper {
    public AppRoleView toView(AppRole role) {
        var app = role.getApp();
        return new AppRoleView(role.getId().toString(), app.getId().toString(), app.getName(), app.getEntraAppId(),
                role.getName(), role.getDescription(), role.isAuthzRole(), role.getRoleIdentifier(),
                role.isLegacySync(), role.getOrdinal(),
                role.getUserTypeRestriction() != null && Arrays.stream(role.getUserTypeRestriction()).distinct().count() == 1
                        ? role.getUserTypeRestriction()[0].name() : "BOTH",
                role.getFirmTypeRestriction() == null ? List.of()
                        : Arrays.stream(role.getFirmTypeRestriction()).map(Enum::name).distinct().sorted().toList());
    }

    public AppRoleView toView(ProfileTrialInput.Role role) {
        return new AppRoleView(role.id().toString(), role.appId().toString(), role.appName(), role.appEntraClientId(),
                role.name(), role.description(), role.authzRole(), role.identifier(), role.legacySync(), role.ordinal(),
                role.userTypes().size() == 1 ? role.userTypes().iterator().next().name() : "BOTH",
                role.firmTypes().stream().map(Enum::name).sorted().toList());
    }
}
