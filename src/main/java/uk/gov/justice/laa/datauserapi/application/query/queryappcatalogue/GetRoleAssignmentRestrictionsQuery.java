package uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue;

import java.util.UUID;

import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

/** {@code appId} is the internal SiLAS ID of the application of the role being assigned; {@code null} for all. */
@RequiresPermission(Permission.VIEW_LAA_APP_METADATA)
public record GetRoleAssignmentRestrictionsQuery(UUID appId, int pageNumber, int pageSize)
        implements AppCatalogueQuery {}
