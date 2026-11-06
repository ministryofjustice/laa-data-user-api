package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateUserAccountResult(
        boolean success,
        String message,
        String userEntraObjectId,
        UUID userProfileId
) {}
