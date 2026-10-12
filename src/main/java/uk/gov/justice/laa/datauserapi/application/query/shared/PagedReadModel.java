package uk.gov.justice.laa.datauserapi.application.query.shared;

import uk.gov.justice.laa.datauserapi.application.query.dto.PageMetadata;

import java.util.Collection;

public record PagedReadModel<READ_MODEL> (
        Collection<READ_MODEL> items,
        PageMetadata page
){}
