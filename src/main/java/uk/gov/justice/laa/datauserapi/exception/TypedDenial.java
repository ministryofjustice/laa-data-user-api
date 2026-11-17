package uk.gov.justice.laa.datauserapi.exception;

/**
 * An authorisation denial that carries a typed reason for server-side logging. The exception handler reads it
 * through this contract, so it does not depend on the security package. The reason is never returned to clients.
 */
public interface TypedDenial {

    Enum<?> reason();
}
