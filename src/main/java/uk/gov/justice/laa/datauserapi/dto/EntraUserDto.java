package uk.gov.justice.laa.datauserapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.datauserapi.model.InvitationStatus;
import uk.gov.justice.laa.datauserapi.model.UserStatus;

import java.io.Serial;
import java.io.Serializable;

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
    private String entraOid;
    private String firstName;
    private String lastName;
    private boolean multiFirmUser;
    private UserStatus userStatus;
    @Builder.Default
    private boolean enabled = true;
    private String disabledBy;
    private boolean mailOnly;
    private InvitationStatus invitationStatus;
    private boolean ccmsEbsUser;
}
