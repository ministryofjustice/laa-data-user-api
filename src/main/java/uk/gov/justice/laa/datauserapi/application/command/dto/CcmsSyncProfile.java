package uk.gov.justice.laa.datauserapi.application.command.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record CcmsSyncProfile(UUID id, String entraOid, UUID legacyUserId, String firstName, String lastName,
                              String email, UserType userType, String vendorNumber,
                              Map<String, Set<String>> rolesByApplication) {
    public CcmsSyncProfile {
        rolesByApplication = rolesByApplication.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}
