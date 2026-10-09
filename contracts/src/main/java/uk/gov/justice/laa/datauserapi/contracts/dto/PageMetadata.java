package uk.gov.justice.laa.datauserapi.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PageMetadata(
        @JsonProperty("page_number")
        int pageNumber,

        @JsonProperty("page_size")
        int pageSize,

        @JsonProperty("total_elements")
        long totalElements,

        @JsonProperty("total_pages")
        int totalPages
) {}
