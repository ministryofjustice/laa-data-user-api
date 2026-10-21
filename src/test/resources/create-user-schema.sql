-- Relevant shared-schema tables for provisioning. Production migrations belong to laa-landing-page.
-- Explicit DDL avoids the unrelated invalid enum defaults in the existing Hibernate entity metadata.
CREATE TYPE firm_type_enum AS ENUM ('LEGAL_SERVICES_PROVIDER', 'CHAMBERS', 'ADVOCATE');

CREATE TABLE firm (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    code VARCHAR(255) UNIQUE,
    type firm_type_enum NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    parent_firm_id UUID REFERENCES firm(id)
);

CREATE TABLE app (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    app_type VARCHAR(255) NOT NULL,
    ordinal INTEGER NOT NULL DEFAULT 0,
    url VARCHAR(255) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    security_group_oid VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE app_role (
    id UUID PRIMARY KEY,
    app_id UUID NOT NULL REFERENCES app(id),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    ordinal INTEGER NOT NULL DEFAULT 0,
    legacy_sync BOOLEAN NOT NULL DEFAULT FALSE,
    authz_role BOOLEAN NOT NULL DEFAULT FALSE,
    firm_type_restriction firm_type_enum[],
    user_type_restriction TEXT[]
);

CREATE TABLE entra_user (
    id UUID PRIMARY KEY,
    entra_oid VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL CHECK (status IN ('ACTIVE', 'DEACTIVATED', 'ACTIVATION_REQUIRED')),
    multi_firm_user BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    mail_only BOOLEAN DEFAULT FALSE,
    ccms_ebs_user BOOLEAN NOT NULL DEFAULT FALSE,
    created_date TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    last_modified_date TIMESTAMP,
    last_modified_by VARCHAR(255)
);
CREATE INDEX ON entra_user (lower(email));

CREATE TABLE user_profile (
    id UUID PRIMARY KEY,
    entra_user_id UUID NOT NULL REFERENCES entra_user(id),
    firm_id UUID REFERENCES firm(id),
    user_type VARCHAR(255) NOT NULL CHECK (user_type IN ('INTERNAL', 'EXTERNAL')),
    active_profile BOOLEAN NOT NULL,
    legacy_user_id UUID,
    status VARCHAR(255) NOT NULL CHECK (status IN ('PENDING', 'COMPLETE')),
    silas_status VARCHAR(255) NOT NULL CHECK (silas_status IN
        ('ACTIVATION_REQUIRED', 'NO_ACCESS_ASSIGNED', 'NO_FIRMS_LINKED', 'COMPLETE', 'UNKNOWN')),
    last_sync_successful BOOLEAN NOT NULL DEFAULT FALSE,
    unrestricted_office_access BOOLEAN NOT NULL DEFAULT FALSE,
    created_date TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    last_modified_date TIMESTAMP,
    last_modified_by VARCHAR(255)
);

CREATE TABLE role_permission (
    app_role_id UUID NOT NULL REFERENCES app_role(id),
    permission VARCHAR(255) NOT NULL,
    PRIMARY KEY (app_role_id, permission)
);

CREATE TABLE user_profile_app_role (
    user_profile_id UUID NOT NULL REFERENCES user_profile(id),
    app_role_id UUID NOT NULL REFERENCES app_role(id),
    PRIMARY KEY (user_profile_id, app_role_id)
);

CREATE TABLE office (
    id UUID PRIMARY KEY,
    firm_id UUID NOT NULL REFERENCES firm(id)
);

CREATE TABLE user_profile_office (
    user_profile_id UUID NOT NULL REFERENCES user_profile(id),
    office_id UUID NOT NULL REFERENCES office(id),
    PRIMARY KEY (user_profile_id, office_id)
);
