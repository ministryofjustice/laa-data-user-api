package uk.gov.justice.laa.datauserapi.contracts.dto;

// The app* fields describe the application of the role being assigned (appRoleId), not of the assigning role.
public record RoleAssignmentRestrictionView(
        String appRoleId,
        String appRoleName,
        String appId,
        String appEntraClientId,
        String appName,
        String assigningAppRoleId,
        String assigningAppRoleName
) {}
