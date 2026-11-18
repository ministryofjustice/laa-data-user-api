package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

public record CurrentUserPermissionsResponse(List<String> permissions) {

    public CurrentUserPermissionsResponse {
        permissions = List.copyOf(permissions);
    }
}
