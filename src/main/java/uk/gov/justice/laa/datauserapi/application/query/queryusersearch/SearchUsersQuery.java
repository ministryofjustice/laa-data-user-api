package uk.gov.justice.laa.datauserapi.application.query.queryusersearch;

import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;

public record SearchUsersQuery(
        UserSearchCriteria criteria,
        String actorOid
) {}
