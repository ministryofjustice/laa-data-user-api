package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.springframework.stereotype.Component;

import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Derives the target-user facts evaluated by the account visibility policies. */
@Component
public class UserAccountVisibilityContextFactory {

    public UserAccountVisibilityContext fromUser(EntraUserDto user) {
        boolean hasInternalProfile = hasProfileOfType(user, UserType.INTERNAL);
        boolean hasExternalProfile = hasProfileOfType(user, UserType.EXTERNAL);

        // If account has no profiles, treat as EXTERNAL if multiFirmUser, else INTERNAL
        if (user.getUserProfiles().isEmpty()) {
            boolean isMultiFirmUser = user.isMultiFirmUser();
            hasExternalProfile = isMultiFirmUser;
            hasInternalProfile = !isMultiFirmUser;
        }

        // Collect ALL firm IDs from external profiles
        Set<UUID> externalFirmIds = user.getUserProfiles().stream()
                .filter(profile -> profile.getUserType() == UserType.EXTERNAL)
                .map(profile -> profile.getFirm() != null ? profile.getFirm().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return new UserAccountVisibilityContext(true, hasInternalProfile, hasExternalProfile,
                externalFirmIds, user.getEntraOid());
    }

    private boolean hasProfileOfType(EntraUserDto user, UserType userType) {
        return user.getUserProfiles().stream()
                .map(UserProfileDto::getUserType)
                .anyMatch(userType::equals);
    }
}
