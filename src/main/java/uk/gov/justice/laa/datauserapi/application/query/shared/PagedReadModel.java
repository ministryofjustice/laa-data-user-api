package uk.gov.justice.laa.datauserapi.application.query.shared;


import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;

import java.util.Collection;

@Builder
@SuppressWarnings("checkstyle:RecordTypeParameterName")
public record PagedReadModel<READ_MODEL>(
        Collection<READ_MODEL> items,
        PageMetadata page
){}
