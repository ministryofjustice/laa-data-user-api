package uk.gov.justice.laa.datauserapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.UserStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntraUserDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String id;
    private String email;
    private String fullName;
    private String lastLoggedIn;
    private LocalDateTime lastSyncedOn;
    private LocalDateTime createdDate;
    private LocalDateTime lastModified;
    private String entraOid;
    private String firstName;
    private String lastName;
    private boolean multiFirmUser;
    private UserStatus userStatus;
    private UserAccountStatus userAccountStatus;
    private Set<UserProfile> userProfiles;
    @Builder.Default
    private boolean active = true;
    private String deactivatedBy;
    private boolean mailOnly;
    private InvitationStatus invitationStatus;
    private boolean ccmsEbsUser;
}
