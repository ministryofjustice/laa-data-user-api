package uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue;

import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

@RequiresPermission(Permission.VIEW_LAA_APP_METADATA)
public record GetAppsQuery() implements AppCatalogueQuery {}
