package uk.gov.justice.laa.datauserapi.application.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A single row of the User Access Audit CSV export.
 *
 * <p>Column names and order are the existing, preserved contract from the legacy export and must
 * not change without a corresponding spec/AC change: {@code Name, Email, Firm name, Firm ID,
 * Multi-firm, Provider Admin, App Access, SiLAS Account Status, Roles Assigned}.
 */
@JsonPropertyOrder({
    "Name", "Email", "Firm name", "Firm ID", "Multi-firm", "Provider Admin",
    "App Access", "SiLAS Account Status", "Roles Assigned"
})
public record AuditUserExportRow(

        @JsonProperty("Name")
        String name,

        @JsonProperty("Email")
        String email,

        @JsonProperty("Firm name")
        String firmName,

        @JsonProperty("Firm ID")
        String firmId,

        @JsonProperty("Multi-firm")
        String multiFirm,

        @JsonProperty("Provider Admin")
        String providerAdmin,

        @JsonProperty("App Access")
        String appAccess,

        @JsonProperty("SiLAS Account Status")
        String silasAccountStatus,

        @JsonProperty("Roles Assigned")
        String rolesAssigned
) {
}
