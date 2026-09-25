package uk.gov.justice.laa.datauserapi.contracts.response;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Read model returned by the data API for a user profile detail query.
 * Maps to {@code UserProfileDetailView} in the OpenAPI spec.
 *
 * @param userProfileId           unique ID of the user profile
 * @param userEntraObjectId       Entra Object ID of the associated user account
 * @param userType                INTERNAL or EXTERNAL
 * @param email                   user's email address
 * @param fullName                first and last names
 * @param firmId                  firm ID (null for internal users)
 * @param firmName                firm name (null for internal users)
 * @param accountStatus           ACTIVE, DISABLED, etc.
 * @param profileStatus           ACTIVE, INACTIVE, etc.
 * @param activeProfile           whether this is the user's currently active profile (multi firm)
 * @param hasAppRoles             whether this profile has any app roles assigned
 * @param unrestrictedOfficeAccess whether the user has unrestricted office access
 */
public record UserProfileDetailResponse (
        @NotNull
        UUID userProfileId,

        String userEntraObjectId,

        @NotNull
        String userType,

        @NotNull
        @Email
        String email,

        @NotNull
        String fullName,

        UUID firmId,

        String firmName,

        @NotNull
        String accountStatus,

        @NotNull
        String profileStatus,

        boolean activeProfile,

        boolean hasAppRoles,

        boolean unrestrictedOfficeAccess
) {}
