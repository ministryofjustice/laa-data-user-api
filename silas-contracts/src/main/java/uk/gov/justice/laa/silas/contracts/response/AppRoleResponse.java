package uk.gov.justice.laa.silas.contracts.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.silas.contracts.enums.UserType;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppRoleResponse {

    private UUID appRoleId;
    private UUID appId;
    private String appName;
    private String name;
    private String description;
    private boolean authorizationRole;
    private boolean legacySync;
    private int ordinal;
    private UserType userTypeRestriction;
}
