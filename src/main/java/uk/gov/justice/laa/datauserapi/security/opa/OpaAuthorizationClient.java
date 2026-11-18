package uk.gov.justice.laa.datauserapi.security.opa;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import uk.gov.justice.laa.datauserapi.exception.OpaUnavailableException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialDecision;

@Component
public class OpaAuthorizationClient {

    private final RestClient restClient;

    public OpaAuthorizationClient(RestClient opaRestClient) {
        this.restClient = opaRestClient;
    }

    public boolean isAllowed(OpaAuthorizationInput input) {
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1/data/authz/allow")
                    .body(toRequest(input))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new OpaUnavailableException("OPA authorization request failed", exception);
        }

        JsonNode result = response == null ? null : response.get("result");
        if (result == null || !result.isBoolean()) {
            throw new OpaUnavailableException("OPA authorization response did not contain a boolean decision");
        }
        return result.booleanValue();
    }

    public Optional<List<String>> callerPermissions(List<String> roles) {
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1/data/authz/permissions/caller_permissions")
                    .body(Map.of("input", Map.of("roles", roles)))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new OpaUnavailableException("OPA caller permissions request failed", exception);
        }

        JsonNode result = response == null ? null : response.get("result");
        if (result == null || !result.isObject()
                || !result.has("allow") || !result.get("allow").isBoolean()
                || !result.has("permissions") || !result.get("permissions").isArray()) {
            throw new OpaUnavailableException("OPA caller permissions response contained an invalid decision");
        }
        TreeSet<String> permissions = new TreeSet<>();
        for (JsonNode permission : result.get("permissions")) {
            if (!permission.isString() || !permission.stringValue().matches("[A-Z][A-Z0-9_]*")) {
                throw new OpaUnavailableException("OPA caller permissions response contained an invalid permission");
            }
            permissions.add(permission.stringValue());
        }
        boolean allowed = result.get("allow").booleanValue();
        if (allowed == permissions.isEmpty()) {
            throw new OpaUnavailableException("OPA caller permissions response contained an inconsistent decision");
        }
        return allowed ? Optional.of(List.copyOf(permissions)) : Optional.empty();
    }

    public ProfileTrialDecision profileDecision(ProfileTrialInput input) {
        JsonNode response;
        try {
            response = restClient.post().uri("/v1/data/authz/trial")
                    .body(Map.of("input", input)).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new OpaUnavailableException("OPA profile trial request failed", exception);
        }
        JsonNode result = response == null ? null : response.get("result");
        if (result == null || !result.isObject() || !result.path("allow").isBoolean()
                || !result.path("reason").isString() || !result.path("queryFilters").isObject()) {
            throw new OpaUnavailableException("OPA profile trial response contained an invalid decision");
        }
        JsonNode filters = result.get("queryFilters");
        if (filters.size() != 3 || !filters.has("firmId") || !(filters.get("firmId").isNull() || filters.get("firmId").isString())
                || !filters.path("userTypes").isArray() || !filters.path("roleIds").isArray()) {
            throw new OpaUnavailableException("OPA profile trial response contained invalid filters");
        }
        Set<UserType> types = new HashSet<>();
        Set<UUID> roles = new HashSet<>();
        UUID firm;
        ProfileTrialDecision.Reason reason;
        try {
            reason = ProfileTrialDecision.Reason.valueOf(result.get("reason").stringValue());
            firm = filters.get("firmId").isNull() ? null : canonicalUuid(filters.get("firmId").stringValue());
            for (JsonNode type : filters.get("userTypes")) {
                if (!type.isString()) {
                    throw new IllegalArgumentException("Invalid user type");
                }
                types.add(UserType.valueOf(type.stringValue()));
            }
            for (JsonNode role : filters.get("roleIds")) {
                if (!role.isString()) {
                    throw new IllegalArgumentException("Invalid role ID");
                }
                roles.add(canonicalUuid(role.stringValue()));
            }
        } catch (IllegalArgumentException exception) {
            throw new OpaUnavailableException("OPA profile trial response contained unsupported filter values", exception);
        }
        boolean allow = result.get("allow").booleanValue();
        if (allow != (reason == ProfileTrialDecision.Reason.ALLOWED)
                || !input.roles().keySet().containsAll(roles)
                || (!allow && (firm != null || !types.isEmpty() || !roles.isEmpty()))
                || (allow && types.isEmpty())
                || (allow && input.target() != null && !types.contains(input.target().userType()))
                || (allow && input.target() != null && firm != null && !firm.equals(input.target().firmId()))
                || (!roles.isEmpty() && input.operation() != uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialOperation.ASSIGNABLE)
                || (input.actor().userType() == UserType.EXTERNAL && allow
                && (firm == null || !firm.equals(input.actor().firmId()) || types.contains(UserType.INTERNAL)))) {
            throw new OpaUnavailableException("OPA profile trial response contained inconsistent constraints");
        }
        return new ProfileTrialDecision(allow, reason, new ProfileTrialDecision.Filters(firm, types, roles));
    }

    private static UUID canonicalUuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) {
            throw new IllegalArgumentException("Non-canonical UUID");
        }
        return id;
    }

    private static Map<String, Object> toRequest(OpaAuthorizationInput input) {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("entra_oid", input.actor().entraOid());
        actor.put("user_type", input.actor().userType());
        actor.put("firm_id", input.actor().firmId());
        actor.put("silas_role_identifiers", input.actor().silasRoleIdentifiers());
        actor.put("permissions", input.actor().permissions());

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("entra_oid", input.target().entraOid());
        target.put("user_type", input.target().userType());
        target.put("firm_id", input.target().firmId());
        target.put("multi_firm", input.target().multiFirm());
        target.put("active", input.target().active());
        target.put("deactivation_type", input.target().deactivationType());

        return Map.of("input", Map.of(
                "action", input.action(),
                "actor", actor,
                "target", target));
    }

}
