package uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser;

public record GetActiveProfileQuery(
        String userEntraObjectId,
        String actorOid
){}
