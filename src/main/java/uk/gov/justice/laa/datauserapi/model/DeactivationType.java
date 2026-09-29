package uk.gov.justice.laa.datauserapi.model;

import lombok.Getter;

/**
 * Represents the delegation level of the user who deactivated an external user account.
 * Stored on {@code entra_user.disable_type} at the time of deactivation so that enable
 * authorisation can be enforced without depending on the disabling user's current roles.
 *
 * <p>The hierarchy (lowest to highest delegation):
 * <ol>
 *   <li>{@link #NONE}      – deactivated by a manual sync process (or unattributed); all roles permitted to re-activate</li>
 *   <li>{@link #SYNC}      – deactivated by an automated sync process; only EUM/EUA or higher can re-activate</li>
 *   <li>{@link #FIRM}      – deactivated by a Firm User Manager; any FUM (same firm), EUM/EUA or higher can re-activate</li>
 *   <li>{@link #LAA}       – deactivated by an External User Manager or External User Admin; only EUM/EUA or higher</li>
 *   <li>{@link #PRIVILEGED} – deactivated by Security Response or Global Admin; only GA or SR can re-activate</li>
 * </ol>
 *
 * <p>A {@code NULL} value in the database means to deactivate was not attributed to a known
 * actor (legacy data before this field existed). In that case only internal/LAA delegation
 * roles may re-enable the user.
 */
@Getter
public enum DeactivationType {

    /**
     * Deactivated by an automated external user sync process.
     * Only External User Manager / Admin or higher can re-activate.
     */
    SYNC("Sync"),

    /**
     * Deactivated by a manual user sync process, or an unattributed deactivate before full tracking was introduced.
     * All roles are permitted to re-activate.
     */
    NONE("None"),

    /**
     * Deactivated by a Firm User Manager.
     * A FUM from the same firm, or any EUM/EUA or higher, can re-activate.
     */
    FIRM("Firm"),

    /**
     * Deactivated by an External User Manager or External User Admin.
     * Only External User Manager / Admin or higher can re-activate.
     */
    LAA("LAA"),

    /**
     * Deactivated by Security Response or Global Admin.
     * Only Security Response or Global Admin can re-activate.
     */
    PRIVILEGED("Privileged");

    private final String displayName;

    DeactivationType(String displayName) {
        this.displayName = displayName;
    }

}
