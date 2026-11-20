package uk.gov.justice.laa.datauserapi.contracts.response;

import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;

import java.util.List;

public record DeletedUserAuditPage(
        List<DeletedUserAuditView> items,
        PageMetadata page
) {}
