package uk.gov.justice.laa.datauserapi.application.query.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.service.CurrentUserPermissionsService;
import uk.gov.justice.laa.datauserapi.contracts.response.CurrentUserPermissionsResponse;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;

@RestController
@RequiresReadScope
public class CurrentUserPermissionsController {

    private final CurrentUserPermissionsService service;

    public CurrentUserPermissionsController(CurrentUserPermissionsService service) {
        this.service = service;
    }

    @GetMapping("/current-user/permissions")
    public ResponseEntity<CurrentUserPermissionsResponse> permissions(@AuthenticationPrincipal Jwt jwt) {
        Object claim = jwt.getClaims().get("roles");
        if (!(claim instanceof List<?> roles) || roles.stream().anyMatch(role -> !(role instanceof String))) {
            throw new AccessDeniedException("Caller roles are missing or malformed");
        }
        List<String> roleNames = roles.stream().map(String.class::cast).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.permissions(roleNames));
    }
}
