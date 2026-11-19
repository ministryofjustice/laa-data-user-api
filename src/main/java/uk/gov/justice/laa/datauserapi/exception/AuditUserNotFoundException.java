package uk.gov.justice.laa.datauserapi.exception;

/**
 * Raised when the target of an audit query does not exist. Unlike {@link UserNotFoundException}, which conceals
 * existence behind a 403, audit queries are specified to return 404 for an unknown target.
 */
public class AuditUserNotFoundException extends ResourceNotFoundException {

    @Override
    public String groupName() {
        return "audit_user_not_found";
    }

    public AuditUserNotFoundException(String userEntraObjectId) {
        super("Audit user not found with ID: " + userEntraObjectId);
    }
}
