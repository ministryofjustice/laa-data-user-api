package uk.gov.justice.laa.datauserapi.application.query.queryuserprofilesbyentrauser;

public record GetUserProfilesQuery(
        String userEntraObjectId,
        String actorOid
){}
