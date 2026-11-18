package uk.gov.justice.laa.datauserapi.contracts.dto;

/**
 * Read model for a role assignment restriction. The {@code app*} fields describe the application of the role being
 * assigned ({@code appRoleId}), not the application of the assigning role.
 */
public record RoleAssignmentRestrictionView(
        String appRoleId,
        String appRoleName,
        String appId,
        String appEntraClientId,
        String appName,
        String assigningAppRoleId,
        String assigningAppRoleName
) {}
