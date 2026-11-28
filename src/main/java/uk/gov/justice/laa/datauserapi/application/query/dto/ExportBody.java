package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A fully-authorised export ready to be streamed to a client. Returned by
 * {@code ExportAuditUsersHandler.handle(...)} so that RBAC authorisation (which runs
 * synchronously inside {@code handle()}, before this callback is even constructed) always
 * happens before any data is fetched or written; {@link #writeTo} is invoked later, by the
 * controller, to perform the actual fetch-and-write work.
 */
@FunctionalInterface
public interface ExportBody {

    void writeTo(OutputStream outputStream) throws IOException;
}
