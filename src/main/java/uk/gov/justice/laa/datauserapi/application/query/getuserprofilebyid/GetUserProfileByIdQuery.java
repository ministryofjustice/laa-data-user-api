package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid;

import java.util.UUID;

public record GetUserProfileByIdQuery(
        UUID id
) {}
