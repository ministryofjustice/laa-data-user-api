package uk.gov.justice.laa.datauserapi.application.command.dto;

import java.util.UUID;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

public record RoleNotificationDetails(UUID profileId, String entraOid, UUID legacyUserId, UserType userType, String firmCode,
                                      String firstName, String lastName, String email) {
}
