package uk.gov.justice.laa.datauserapi.exception;

public class InvalidActorContextException extends RuntimeException {

    public InvalidActorContextException(String message) {
        super(message);
    }

    public InvalidActorContextException(String message, Throwable cause) {
        super(message, cause);
    }

    public static InvalidActorContextException noActiveProfile(String actorOid) {
        return new InvalidActorContextException("Actor has no active profile: " + actorOid);
    }

    public static InvalidActorContextException undeterminedUserType() {
        return new InvalidActorContextException("Actor user type cannot be determined");
    }
}
