package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.application.query.dto.AuditProfileRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Derives the target-user facts evaluated by the account visibility policies. */
@Component
public class UserAccountVisibilityContextFactory {

    public UserAccountVisibilityContext fromUser(EntraUserDto user) {
        List<AuditProfileRow> profiles = user.getUserProfiles().stream()
                .map(profile -> new AuditProfileRow(profile.getId(), profile.isActiveProfile(), profile.getUserType(),
                        profile.getFirm() == null ? null : profile.getFirm().getId()))
                .toList();
        return fromFacts(user.getEntraOid(), user.isMultiFirmUser(), profiles);
    }

    public UserAccountVisibilityContext fromFacts(String entraOid, boolean multiFirmUser,
                                                  Collection<AuditProfileRow> profiles) {
        boolean hasInternalProfile = hasProfileOfType(profiles, UserType.INTERNAL);
        boolean hasExternalProfile = hasProfileOfType(profiles, UserType.EXTERNAL);

        // If account has no profiles, treat as EXTERNAL if multiFirmUser, else INTERNAL
        if (profiles.isEmpty()) {
            hasExternalProfile = multiFirmUser;
            hasInternalProfile = !multiFirmUser;
        }

        // Collect ALL firm IDs from external profiles
        Set<UUID> externalFirmIds = profiles.stream()
                .filter(profile -> profile.userType() == UserType.EXTERNAL)
                .map(AuditProfileRow::firmId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return new UserAccountVisibilityContext(true, hasInternalProfile, hasExternalProfile,
                externalFirmIds, entraOid);
    }

    private boolean hasProfileOfType(Collection<AuditProfileRow> profiles, UserType userType) {
        return profiles.stream().map(AuditProfileRow::userType).anyMatch(userType::equals);
    }
}
