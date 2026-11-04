package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Collection;

@Builder
@SuppressWarnings("checkstyle:RecordTypeParameterName")
public record PagedView<VIEW>(

        @JsonProperty("items")
        Collection<VIEW> items,

        @JsonProperty("page")
        PagedViewMetadata page

) {}
