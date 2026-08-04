package uk.gov.justice.laa.silas.contracts.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfficeResponse {

    private String officeId;
    private String firmId;
    private String postCode;
    private String addressLine1;
    private String addressLine2;
    private String addressLine3;
    private String city;
}
