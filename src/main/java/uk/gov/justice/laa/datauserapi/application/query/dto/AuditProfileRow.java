package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.UUID;

/** The facts about a profile needed to decide visibility, ordering and counts, without its roles or offices. */
public record AuditProfileRow(UUID id, boolean activeProfile, UserType userType, UUID firmId) {
}
