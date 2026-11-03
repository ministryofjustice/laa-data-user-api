package uk.gov.justice.laa.datauserapi.client.ts.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeAccountEnabledResponse {
    private boolean success;
    private String message;
}
