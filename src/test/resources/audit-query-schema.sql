CREATE TABLE entra_user (
    id UUID PRIMARY KEY, entra_oid VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL, last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE, status VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL, multi_firm_user BOOLEAN NOT NULL DEFAULT FALSE,
    mail_only BOOLEAN DEFAULT FALSE, ccms_ebs_user BOOLEAN NOT NULL DEFAULT FALSE,
    invitation_status VARCHAR(255), disabled_by UUID, disable_type VARCHAR(20),
    last_synced_on TIMESTAMP, created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'seed', last_modified_date TIMESTAMP,
    last_modified_by VARCHAR(255)
);
CREATE TABLE disable_user_reason (
    id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL, description VARCHAR(255) NOT NULL,
    entra_description VARCHAR(255) NOT NULL, user_selectable BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE delete_user_reason (
    id UUID PRIMARY KEY, code VARCHAR(255) NOT NULL UNIQUE, label VARCHAR(255) NOT NULL,
    editable_by_internal_user BOOLEAN NOT NULL DEFAULT FALSE,
    editable_by_external_user BOOLEAN NOT NULL DEFAULT FALSE,
    system_generated BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE user_account_status_audit (
    id UUID PRIMARY KEY, status_changed_date TIMESTAMP NOT NULL, status_changed_by VARCHAR(255) NOT NULL,
    status_change VARCHAR(255) NOT NULL, entra_user_id UUID, user_email VARCHAR(255), user_name VARCHAR(511),
    disable_user_reason_id UUID REFERENCES disable_user_reason, disable_type VARCHAR(20),
    delete_user_reason_id UUID REFERENCES delete_user_reason, comments VARCHAR(500),
    deleted_user_created_date TIMESTAMP
);
