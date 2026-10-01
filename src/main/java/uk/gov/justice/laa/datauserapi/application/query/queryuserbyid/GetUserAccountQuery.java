package uk.gov.justice.laa.datauserapi.application.query.queryuserbyid;

public record GetUserAccountQuery(
        String userEntraObjectId,
        String actorOid
) {}
