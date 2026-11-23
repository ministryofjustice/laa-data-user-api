package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.dto.RoleAssignmentRestrictionView;

public record RoleAssignmentRestrictionViewPage(
        List<RoleAssignmentRestrictionView> items,
        PageMetadata page
) {}
