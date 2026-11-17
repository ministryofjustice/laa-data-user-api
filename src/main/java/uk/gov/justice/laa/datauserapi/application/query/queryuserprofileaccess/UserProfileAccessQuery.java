package uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess;

import java.util.UUID;

/** A query that reads the access (roles, apps or offices) of one user profile. */
public interface UserProfileAccessQuery {

    UUID userProfileId();
}
