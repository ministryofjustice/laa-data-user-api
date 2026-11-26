package authz

import future.keywords.if

default allow := false

allow if {
	input.action == "deactivate"
	input.actor.permissions[_] == "DISABLE_EXTERNAL_USER"
	target_is_external
	not_self
	firm_scope
}

allow if {
	input.action == "reactivate"
	input.actor.permissions[_] == "ENABLE_EXTERNAL_USER"
	target_is_external
	not_self
	firm_scope
	input.target.active == false
	reactivation_role_allowed
}

target_is_external if {
	input.target.user_type == "EXTERNAL"
}

not_self if {
	is_string(input.actor.entra_oid)
	is_string(input.target.entra_oid)
	lower(input.actor.entra_oid) != lower(input.target.entra_oid)
}

firm_scope if {
	input.actor.user_type == "INTERNAL"
}

firm_scope if {
	input.actor.user_type == "EXTERNAL"
	is_string(input.actor.firm_id)
	is_string(input.target.firm_id)
	input.target.multi_firm == false
	input.actor.firm_id == input.target.firm_id
}

reactivation_role_allowed if {
	input.target.deactivation_type == "NONE"
}

reactivation_role_allowed if {
	input.target.deactivation_type == "FIRM"
	is_privileged
}

reactivation_role_allowed if {
	input.target.deactivation_type == "FIRM"
	is_external_user_management
}

reactivation_role_allowed if {
	input.target.deactivation_type == "FIRM"
	has_role("Firm User Manager")
}

reactivation_role_allowed if {
	input.target.deactivation_type == "SYNC"
	is_privileged
}

reactivation_role_allowed if {
	input.target.deactivation_type == "SYNC"
	is_external_user_management
}

reactivation_role_allowed if {
	input.target.deactivation_type == "LAA"
	is_privileged
}

reactivation_role_allowed if {
	input.target.deactivation_type == "LAA"
	is_external_user_management
}

reactivation_role_allowed if {
	input.target.deactivation_type == "PRIVILEGED"
	is_privileged
}

reactivation_role_allowed if {
	input.target.deactivation_type == null
	is_privileged
}

reactivation_role_allowed if {
	input.target.deactivation_type == null
	is_external_user_management
}

reactivation_role_allowed if {
	input.target.deactivation_type == null
	has_role("Internal User Manager")
}

is_privileged if {
	has_role("Global Admin")
}

is_privileged if {
	has_role("Security Response")
}

is_external_user_management if {
	has_role("External User Admin")
}

is_external_user_management if {
	has_role("External User Manager")
}

is_external_user_management if {
	has_role("External User Support")
}

has_role(role) if {
	input.actor.silas_role_identifiers[_] == role
}
