package uk.gov.justice.laa.datauserapi.shared;

import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;


@SuppressWarnings("checkstyle:ClassTypeParameterName")
public abstract class QueryMapper<REQUEST, QUERY> {

    public abstract QUERY map(REQUEST argument, AuthenticatedEntraUser authenticatedUser);

    public QUERY map(REQUEST argument) {
        return map(argument, null);
    }
}
