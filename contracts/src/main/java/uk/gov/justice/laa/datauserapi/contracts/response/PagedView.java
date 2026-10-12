package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

public record PagedView<VIEW>(

        @JsonProperty("items")
        Collection<VIEW> items,

        @JsonProperty("page")
        PagedViewMetadata page

) {}
