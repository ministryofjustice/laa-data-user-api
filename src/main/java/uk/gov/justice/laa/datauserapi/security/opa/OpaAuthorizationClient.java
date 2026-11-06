package uk.gov.justice.laa.datauserapi.security.opa;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uk.gov.justice.laa.datauserapi.exception.OpaUnavailableException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpaAuthorizationClient {

    private final RestClient restClient;

    public OpaAuthorizationClient(RestClient opaRestClient) {
        this.restClient = opaRestClient;
    }

    public boolean isAllowed(OpaAuthorizationInput input) {
        OpaResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/data/authz/allow")
                    .body(toRequest(input))
                    .retrieve()
                    .body(OpaResponse.class);
        } catch (RestClientException exception) {
            throw new OpaUnavailableException("OPA authorization request failed", exception);
        }

        if (response == null || response.result() == null) {
            throw new OpaUnavailableException("OPA authorization response did not contain a decision");
        }
        return response.result();
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

    private record OpaResponse(Boolean result) {
    }
}
