package uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue;

import java.util.UUID;

import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

/** {@code appRoleId} is the local SiLAS app role ID. */
@RequiresPermission(Permission.VIEW_LAA_APP_METADATA)
public record GetAppRoleByIdQuery(UUID appRoleId) implements AppCatalogueQuery {}
