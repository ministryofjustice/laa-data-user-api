package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

public record CurrentUserPermissionsResponse(List<String> permissions) {

    public static final int MAX_PERMISSIONS = 100;

    public CurrentUserPermissionsResponse {
        permissions = List.copyOf(permissions);
    }
}
