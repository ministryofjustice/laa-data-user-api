package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TriggerCcmsRoleSyncResult(
        @NotNull @JsonProperty(value = "success", required = true) boolean success,
        @Min(0) @JsonProperty(value = "userProfileCount", required = true) int userProfileCount) {
}
