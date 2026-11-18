package uk.gov.justice.laa.datauserapi.application.command.dto;

import java.util.UUID;

public record RoleOutboxDelivery(long id, UUID eventId, UUID leaseOwner, UUID profileId,
                                String applicationOid, String groupId, String userType, String payload) {
}
