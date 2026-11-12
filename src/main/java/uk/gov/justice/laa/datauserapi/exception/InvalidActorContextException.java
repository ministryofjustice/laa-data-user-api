package uk.gov.justice.laa.datauserapi.exception;

public class InvalidActorContextException extends RuntimeException
        implements ReasonedException<InvalidActorContextReason> {

    private final InvalidActorContextReason reason;

    @Override
    public String groupName() {
        return "invalid_actor_context";
    }

    private InvalidActorContextException(InvalidActorContextReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    @Override
    public InvalidActorContextReason reason() {
        return reason;
    }

    public static InvalidActorContextException noActiveProfile(String actorOid) {
        return new InvalidActorContextException(InvalidActorContextReason.NO_ACTIVE_PROFILE,
                "Actor has no active profile: " + actorOid, null);
    }

    public static InvalidActorContextException undeterminedUserType() {
        return new InvalidActorContextException(InvalidActorContextReason.UNDETERMINED_USER_TYPE,
                "Actor user type cannot be determined", null);
    }

    public static InvalidActorContextException missingActorIdentity() {
        return new InvalidActorContextException(InvalidActorContextReason.MISSING_ACTOR_IDENTITY,
                "Authentication token is missing the actor identity", null);
    }

    public static InvalidActorContextException invalidActorIdentity(Throwable cause) {
        return new InvalidActorContextException(InvalidActorContextReason.INVALID_ACTOR_IDENTITY,
                "Authentication token contains an invalid actor identity", cause);
    }
}
