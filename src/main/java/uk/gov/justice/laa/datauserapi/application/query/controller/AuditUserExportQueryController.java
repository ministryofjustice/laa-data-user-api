package uk.gov.justice.laa.datauserapi.application.query.controller;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uk.gov.justice.laa.datauserapi.application.query.dto.ExportBody;
import uk.gov.justice.laa.datauserapi.application.query.handler.ExportAuditUsersHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Serves the User Access Audit CSV export. Kept separate from {@code UserAccountQueryController}
 * so CSV/streaming/export concerns do not spread into general user account query endpoints.
 */
@RestController
@RequestMapping("/api/v1/queries/audit")
@RequiresAdminScope
@Slf4j
public class AuditUserExportQueryController {

    private static final String TEXT_CSV_VALUE = "text/csv";
    private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").withZone(ZoneOffset.UTC);

    private final ExportAuditUsersHandler handler;
    private final Clock clock;

    public AuditUserExportQueryController(
            ExportAuditUsersHandler handler,
            @Qualifier("auditExportClock") Clock clock) {
        this.handler = handler;
        this.clock = clock;
    }

    @GetMapping(value = "/users/export", produces = TEXT_CSV_VALUE)
    public ResponseEntity<StreamingResponseBody> exportAuditUsers(
            @Valid @ModelAttribute AuditUserExportCriteria criteria,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {

        ExportBody body = handler.handle(new ExportAuditUsersQuery(criteria), authenticatedUser);
        String scope = exportScope(criteria);

        log.info("audit_users_export_started",
                kv("actorEntraOid", authenticatedUser.entraOid()),
                kv("exportScope", scope));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(buildFilename(scope)).build().toString())
                .contentType(MediaType.parseMediaType(TEXT_CSV_VALUE))
                .body(body::writeTo);
    }

    private String exportScope(AuditUserExportCriteria criteria) {
        return criteria.userType() == UserType.INTERNAL ? "INTERNAL" : criteria.firmId();
    }

    private String buildFilename(String scope) {
        String timestamp = FILENAME_TIMESTAMP_FORMAT.format(Instant.now(clock));
        return "user-access-audit_" + scope + "_" + timestamp + "_UTC.csv";
    }
}
