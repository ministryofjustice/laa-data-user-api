CREATE TABLE entra_user (
    id UUID PRIMARY KEY, entra_oid VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL, last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE, status VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL, invitation_status VARCHAR(255),
    multi_firm_user BOOLEAN NOT NULL DEFAULT FALSE, mail_only BOOLEAN DEFAULT FALSE,
    ccms_ebs_user BOOLEAN NOT NULL DEFAULT FALSE, disabled_by UUID,
    disable_type VARCHAR(20), last_synced_on TIMESTAMP,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'seed',
    last_modified_date TIMESTAMP, last_modified_by VARCHAR(255)
);
CREATE TABLE firm (
    id UUID PRIMARY KEY, name VARCHAR(255), code VARCHAR(255),
    type VARCHAR(255), enabled BOOLEAN, parent_firm_id UUID
);
CREATE TABLE app (
    id UUID PRIMARY KEY, name VARCHAR(255), description TEXT, app_type VARCHAR(255),
    ordinal INTEGER, url VARCHAR(255), enabled BOOLEAN,
    entra_app_id VARCHAR(255), entra_oid VARCHAR(255), security_group_oid VARCHAR(255)
);
CREATE TABLE app_role (
    id UUID PRIMARY KEY, name VARCHAR(255), description VARCHAR(255),
    ordinal INTEGER NOT NULL, app_id UUID REFERENCES app,
    authz_role BOOLEAN NOT NULL, legacy_sync BOOLEAN NOT NULL, role_identifier VARCHAR(30),
    firm_type_restriction TEXT[], user_type_restriction TEXT[]
);
CREATE TABLE role_permission (app_role_id UUID REFERENCES app_role, permission VARCHAR(255));
CREATE TABLE office (
    id UUID PRIMARY KEY, code VARCHAR(255), firm_id UUID REFERENCES firm,
    address_line_1 VARCHAR(255), address_line_2 VARCHAR(255), address_line_3 VARCHAR(255),
    city VARCHAR(255), post_code VARCHAR(20)
);
CREATE TABLE user_profile (
    id UUID PRIMARY KEY, entra_user_id UUID NOT NULL REFERENCES entra_user,
    user_type VARCHAR(255) NOT NULL, active_profile BOOLEAN NOT NULL DEFAULT TRUE,
    legacy_user_id UUID, firm_id UUID REFERENCES firm, status VARCHAR(255) NOT NULL DEFAULT 'COMPLETE',
    silas_status VARCHAR(255) NOT NULL, last_sync_successful BOOLEAN NOT NULL DEFAULT FALSE,
    unrestricted_office_access BOOLEAN NOT NULL DEFAULT FALSE,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'seed',
    last_modified_date TIMESTAMP, last_modified_by VARCHAR(255)
);
CREATE INDEX refresh_profile_user_idx ON user_profile(entra_user_id);
CREATE TABLE user_profile_app_role (
    user_profile_id UUID REFERENCES user_profile, app_role_id UUID REFERENCES app_role,
    PRIMARY KEY (user_profile_id, app_role_id)
);
CREATE TABLE user_profile_office (
    user_profile_id UUID REFERENCES user_profile, office_id UUID REFERENCES office,
    PRIMARY KEY (user_profile_id, office_id)
);
