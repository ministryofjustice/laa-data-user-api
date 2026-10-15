package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        @NotNull
        @JsonProperty(value = "type", required = true)
        URI type,

        @NotNull
        @JsonProperty(value = "title", required = true)
        String title,

        @NotNull
        @JsonProperty(value = "status", required = true)
        Integer status,

        @NotNull
        @JsonProperty(value = "detail", required = true)
        String detail,

        @NotNull
        @JsonProperty(value = "instance", required = true)
        URI instance,

        @JsonProperty("errors")
        List<FieldErrorDetail> errors
) {
    public ProblemDetail {
        if (errors != null && errors.size() > 100) {
            throw new IllegalArgumentException("Errors array cannot exceed 100 items");
        }
    }
}
