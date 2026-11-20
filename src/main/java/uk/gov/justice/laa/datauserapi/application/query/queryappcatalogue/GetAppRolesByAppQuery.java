package uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue;

import java.util.UUID;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

/** {@code appId} is the internal SiLAS app ID; a {@code null} {@code userType} returns all roles. */
@RequiresPermission(Permission.VIEW_LAA_APP_METADATA)
public record GetAppRolesByAppQuery(UUID appId, UserType userType) implements AppCatalogueQuery {}
