package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid;

import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.UUID;

@Builder
public record GetUserProfileByIdQuery(
        UUID id,
        UUID firmId,
        UserType userType
) {}
