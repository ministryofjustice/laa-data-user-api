package uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue;

import java.util.UUID;

import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

/** {@code appId} is the internal SiLAS app ID. */
@RequiresPermission(Permission.VIEW_LAA_APP_METADATA)
public record GetAppByIdQuery(UUID appId) implements AppCatalogueQuery {}
