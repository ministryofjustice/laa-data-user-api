package uk.gov.justice.laa.silas.contracts.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnableUserRequest {

    @NotNull(message = "userEntraObjectId is required")
    private UUID userEntraObjectId;
}
