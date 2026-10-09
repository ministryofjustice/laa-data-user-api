package uk.gov.justice.laa.datauserapi.application.query.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchViewList;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmViewPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.service.FirmQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.FirmOfficesResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.FirmPageResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.FirmResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.FirmSearchResponse;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;

@RequiresReadScope
@Validated
@RestController
@RequestMapping("/api/v1/queries/firms")
public class FirmQueryController {

    private final FirmQueryService firmQueryService;

    public FirmQueryController(FirmQueryService firmQueryService) {
        this.firmQueryService = firmQueryService;
    }

    @GetMapping
    public ResponseEntity<FirmPageResponse> queryFirms(
            @RequestParam(name = "firm", required = false) String firm,
            @RequestParam(name = "page_number", defaultValue = "0") @Min(0) int pageNumber,
            @RequestParam(name = "page_size", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        FirmViewPage result = firmQueryService.listFirms(resolveActor(jwt), firm, pageNumber, pageSize);
        return ResponseEntity.ok(new FirmPageResponse(result.items(), result.page()));
    }

    @GetMapping("/{firmId}")
    public ResponseEntity<FirmResponse> queryFirmById(
            @PathVariable @Pattern(regexp = "\\d+", message = "firmId must be numeric") String firmId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new FirmResponse(firmQueryService.getFirmById(resolveActor(jwt), firmId)));
    }

    @GetMapping("/search")
    public ResponseEntity<FirmSearchResponse> queryFirmsSearch(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @AuthenticationPrincipal Jwt jwt) {
        FirmSearchViewList result = firmQueryService.searchFirms(resolveActor(jwt), query, limit);
        return ResponseEntity.ok(new FirmSearchResponse(result.items()));
    }

    @GetMapping("/{firmId}/offices")
    public ResponseEntity<FirmOfficesResponse> queryFirmOffices(
            @PathVariable @Pattern(regexp = "\\d+", message = "firmId must be numeric") String firmId,
            @AuthenticationPrincipal Jwt jwt) {
        OfficeViewList result = firmQueryService.getFirmOffices(resolveActor(jwt), firmId);
        return ResponseEntity.ok(new FirmOfficesResponse(result.items()));
    }

    private UUID resolveActor(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidActorContextException("Authentication token is missing the actor identity");
        }

        String oid = jwt.getClaimAsString("oid");
        if (oid == null || oid.isBlank()) {
            throw new InvalidActorContextException("Authentication token is missing the actor identity");
        }

        try {
            return UUID.fromString(oid);
        } catch (IllegalArgumentException ex) {
            throw new InvalidActorContextException("Authentication token contains an invalid actor identity", ex);
        }
    }
}
