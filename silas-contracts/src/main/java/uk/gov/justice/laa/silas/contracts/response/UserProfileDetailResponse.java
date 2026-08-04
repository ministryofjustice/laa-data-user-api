package uk.gov.justice.laa.silas.contracts.response;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.silas.contracts.enums.UserAccountStatus;
import uk.gov.justice.laa.silas.contracts.enums.UserProfileStatus;
import uk.gov.justice.laa.silas.contracts.enums.UserType;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDetailResponse {

    @NotNull
    private UUID userProfileId;

    @NotNull
    private UserType userType;

    @NotNull
    @NotBlank
    @Email
    private String email;

    @NotNull
    private String fullName;

    private boolean activeProfile;

    private String firmName;

    private String firmId;

    private boolean multiFirmUser;

    @NotNull
    private UserAccountStatus accountStatus;

    @NotNull
    private UserProfileStatus profileStatus;

    private boolean hasAppRoles;

    private UUID userEntraObjectId;

    private boolean unrestrictedOfficeAccess;

    private boolean lastSyncSuccessful;

    @Builder.Default
    private List<AppRoleResponse> roles = List.of();

    @Builder.Default
    private List<OfficeResponse> offices = List.of();
}
