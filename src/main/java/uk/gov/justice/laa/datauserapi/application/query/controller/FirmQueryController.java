package uk.gov.justice.laa.datauserapi.application.query.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchViewList;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmView;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmViewPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.service.FirmQueryService;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/queries/firms")
public class FirmQueryController {

    private final FirmQueryService firmQueryService;

    public FirmQueryController(FirmQueryService firmQueryService) {
        this.firmQueryService = firmQueryService;
    }

    @GetMapping
    public ResponseEntity<FirmViewPage> queryFirms(
            @RequestParam(name = "firm", required = false) String firm,
            @RequestParam(name = "page_number", defaultValue = "0") @Min(0) int pageNumber,
            @RequestParam(name = "page_size", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        FirmViewPage result = firmQueryService.listFirms(resolveActor(jwt), firm, pageNumber, pageSize);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{firmId}")
    public ResponseEntity<FirmView> queryFirmById(
            @PathVariable @Pattern(regexp = "\\d+", message = "firmId must be numeric") String firmId,
            @AuthenticationPrincipal Jwt jwt) {
        FirmView result = firmQueryService.getFirmById(resolveActor(jwt), firmId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<FirmSearchViewList> queryFirmsSearch(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @AuthenticationPrincipal Jwt jwt) {
        FirmSearchViewList result = firmQueryService.searchFirms(resolveActor(jwt), query, limit);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{firmId}/offices")
    public ResponseEntity<OfficeViewList> queryFirmOffices(
            @PathVariable @Pattern(regexp = "\\d+", message = "firmId must be numeric") String firmId,
            @AuthenticationPrincipal Jwt jwt) {
        OfficeViewList result = firmQueryService.getFirmOffices(resolveActor(jwt), firmId);
        return ResponseEntity.ok(result);
    }

    private UUID resolveActor(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("oid"));
    }
}
