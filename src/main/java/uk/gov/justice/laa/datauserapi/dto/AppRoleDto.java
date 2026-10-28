package uk.gov.justice.laa.datauserapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppRoleDto implements Comparable<AppRoleDto>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String id;
    private String name;
    private int ordinal;
    private String description;
    private String roleIdentifier;
    private AppDto app;
    private UserType[] userTypeRestriction;
    private boolean changeNotAllowed;
    private boolean legacySync;
    private boolean authzRole;
    private Set<Permission> permissions;
    private FirmType[] firmTypeRestriction;


    @Override
    public int compareTo(@NotNull AppRoleDto o) {
        int appOrdinalComparison = Integer.compare(this.app.getOrdinal(), o.app.getOrdinal());
        if (appOrdinalComparison != 0) {
            return appOrdinalComparison;
        }
        return Integer.compare(this.ordinal, o.ordinal);
    }
}
