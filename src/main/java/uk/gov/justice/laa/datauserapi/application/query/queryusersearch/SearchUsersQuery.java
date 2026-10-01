package uk.gov.justice.laa.datauserapi.application.query.queryusersearch;

import uk.gov.justice.laa.datauserapi.application.query.dto.UserSearchCriteria;

public record SearchUsersQuery(
        UserSearchCriteria criteria,
        String actorOid
) {}
