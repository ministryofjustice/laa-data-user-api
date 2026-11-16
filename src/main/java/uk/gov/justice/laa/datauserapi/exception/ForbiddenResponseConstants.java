package uk.gov.justice.laa.datauserapi.exception;

import java.net.URI;

/** Shared forbidden-response contract for security filters and exception advice. */
public final class ForbiddenResponseConstants {

    public static final URI TYPE = URI.create("https://silas.laa.gov.uk/errors/forbidden");
    public static final String TITLE = "Forbidden";
    public static final String DETAIL = "You do not possess the required scopes or permissions to perform this operation.";
    public static final String ACCESS_DENIED_MESSAGE = "Access denied";

    private ForbiddenResponseConstants() {
    }
}
