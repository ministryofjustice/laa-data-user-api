package uk.gov.justice.laa.datauserapi.application.command.dto;

import java.util.UUID;

public record UserAccountCreationData(String entraOid, UUID profileId) {
}
