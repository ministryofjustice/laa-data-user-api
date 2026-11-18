package uk.gov.justice.laa.datauserapi.exception;

public class ProfileRoleConflictException extends RuntimeException implements ExceptionGroup {
    public ProfileRoleConflictException() {
        super("Profile role authorization facts changed before the protected update");
    }

    public ProfileRoleConflictException(Throwable cause) {
        super("Profile role authorization facts changed before the protected update", cause);
    }

    @Override
    public String groupName() {
        return "profile_role_conflict";
    }
}
