package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsSyncProfile;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.util.stream.Collectors;

@Component
public class CcmsSyncProfileMapper {

    public CcmsSyncProfile toSnapshot(UserProfile profile) {
        var user = profile.getEntraUser();
        if (profile.getLegacyUserId() == null || profile.getUserType() == null || user == null
                || blank(user.getEntraOid()) || profile.getAppRoles() == null
                || profile.getAppRoles().stream().anyMatch(role -> role == null
                    || (role.isLegacySync() && (role.getApp() == null || blank(role.getApp().getEntraOid())
                        || blank(role.getRoleIdentifier()))))
                || (profile.getUserType() == UserType.EXTERNAL
                    && (profile.getFirm() == null || blank(profile.getFirm().getCode())))) {
            throw new CcmsSyncException(CcmsSyncException.Reason.INVALID_PROFILE);
        }
        var roles = profile.getAppRoles().stream()
                .filter(AppRole::isLegacySync)
                .collect(Collectors.groupingBy(role -> role.getApp().getEntraOid(),
                        Collectors.mapping(AppRole::getRoleIdentifier, Collectors.toSet())));
        if (roles.isEmpty()) {
            throw new CcmsSyncException(CcmsSyncException.Reason.NO_SYNC_ROLES);
        }
        return new CcmsSyncProfile(profile.getId(), user.getEntraOid(), profile.getLegacyUserId(),
                user.getFirstName(), user.getLastName(), user.getEmail(), profile.getUserType(),
                profile.getUserType() == UserType.EXTERNAL ? profile.getFirm().getCode() : null, roles);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
