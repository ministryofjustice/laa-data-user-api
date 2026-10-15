package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldErrorDetail(
        @JsonProperty("field")
        String field,

        @JsonProperty("detail")
        String detail
) {}
