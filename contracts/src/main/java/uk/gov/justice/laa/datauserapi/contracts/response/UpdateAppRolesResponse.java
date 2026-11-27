package uk.gov.justice.laa.datauserapi.contracts.response;

public record UpdateAppRolesResponse(boolean success, String message, AppRoleViewList appRoles) {
}
