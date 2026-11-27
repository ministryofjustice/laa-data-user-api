package authz.permissions

import future.keywords.if

test_each_supplied_role_has_exact_permissions if {
	failures := {role |
		expected := expected_permissions[role]
		actual := caller_permissions with input as {"roles": [role]}
		actual != {"allow": true, "permissions": sort(split(expected, " "))}
	}
	count(failures) == 0
	count(expected_permissions) == 12
	count({role | application := role_permissions[_]; application[role]}) == 12
}

test_multiple_roles_return_the_union_without_duplicates if {
	actual := caller_permissions with input as {"roles": ["Internal User Viewer", "External User Viewer", "Audit Export", "Internal User Viewer"]}
	actual == {"allow": true, "permissions": [
		"EXPORT_AUDIT_DATA", "VIEW_AUDIT_TABLE", "VIEW_EXTERNAL_USER",
		"VIEW_FIRM_DIRECTORY", "VIEW_INTERNAL_USER", "VIEW_USER_OFFICE",
	]}
}

test_all_mapped_permissions_fit_the_api_response_limit if {
	permissions := {permission |
		application := role_permissions[_]
		role := application[_]
		permission := role[_]
	}
	count(permissions) <= 100
}

test_unknown_role_does_not_add_permissions_to_recognised_roles if {
	actual := caller_permissions with input as {"roles": ["Unknown Role", "Internal User Viewer"]}
	actual == {"allow": true, "permissions": ["VIEW_AUDIT_TABLE", "VIEW_INTERNAL_USER"]}
}

test_permissions_cannot_be_supplied_by_caller if {
	actual := caller_permissions with input as {
		"roles": ["Internal User Viewer"],
		"permissions": ["DELETE_EXTERNAL_USER"],
	}
	actual == {"allow": true, "permissions": ["VIEW_AUDIT_TABLE", "VIEW_INTERNAL_USER"]}
}

test_invalid_or_unrecognised_roles_deny if {
	inputs := [
		null, true, [], "Global Admin", {}, {"roles": null}, {"roles": []},
		{"roles": "Global Admin"}, {"roles": 1}, {"roles": {}},
		{"roles": [null]}, {"roles": [true]}, {"roles": [42]}, {"roles": [{}]},
		{"roles": ["Global Admin", 42]}, {"roles": ["Global Admin", null]},
		{"roles": ["global admin"]}, {"roles": [" Global Admin"]}, {"roles": ["Global Admin "]},
		{"roles": ["Unknown Role"]}, {"roles": [""]},
		{"roles": [], "permissions": ["DELETE_EXTERNAL_USER"]},
	]
	failures := {i |
		actual := caller_permissions with input as inputs[i]
		actual != {"allow": false, "permissions": []}
	}
	count(failures) == 0
}

# Exact, deduplicated expectations from the supplied application/role/permission mapping.
expected_permissions := {
	"Internal User Manager": "ASSIGN_INTERNAL_USER_ROLES EDIT_INTERNAL_USER REMOVE_INTERNAL_USER_ROLES VIEW_AUDIT_TABLE VIEW_INTERNAL_USER",
	"Internal User Viewer": "VIEW_AUDIT_TABLE VIEW_INTERNAL_USER",
	"External User Viewer": "VIEW_AUDIT_TABLE VIEW_EXTERNAL_USER VIEW_FIRM_DIRECTORY VIEW_USER_OFFICE",
	"Audit Export": "EXPORT_AUDIT_DATA VIEW_AUDIT_TABLE",
	"Multi-firm Delegation": "DELEGATE_EXTERNAL_USER_ACCESS_INTERNAL",
	"SILAS System Administration": "DELETE_LAA_APP_ROLE EDIT_LAA_APP_METADATA TRIGGER_LAA_APP_SYNC VIEW_LAA_APP_METADATA",
	"External User Manager": "ASSIGN_EXTERNAL_USER_ROLES CAN_REQUEST_DELEGATE_ENABLE_USER CAN_TRACK_DELEGATE_ACTIVATION_REQUESTS DISABLE_EXTERNAL_USER EDIT_EXTERNAL_USER EDIT_USER_OFFICE ENABLE_EXTERNAL_USER REMOVE_EXTERNAL_USER_ROLES RESEND_VERIFICATION_EMAIL VIEW_ALL_USER_MULTI_FIRM_PROFILES VIEW_AUDIT_TABLE VIEW_EXTERNAL_USER VIEW_FIRM_DIRECTORY VIEW_USER_OFFICE",
	"External User Admin": "ASSIGN_EXTERNAL_USER_ROLES BULK_DISABLE_FIRM_USERS CAN_MANAGE_DELEGATE_ENABLE_USER CAN_TRACK_DELEGATE_ACTIVATION_REQUESTS CONVERT_USER_TO_MULTI_FIRM CREATE_EXTERNAL_USER DELETE_AUDIT_USER DELETE_EXTERNAL_USER DISABLE_EXTERNAL_USER EDIT_EXTERNAL_USER EDIT_USER_DETAILS EDIT_USER_FIRM EDIT_USER_OFFICE ENABLE_EXTERNAL_USER REMOVE_EXTERNAL_USER_ROLES RESEND_VERIFICATION_EMAIL UPDATE_USER_PROFILE_SILAS_STATUS VIEW_ALL_USER_MULTI_FIRM_PROFILES VIEW_AUDIT_TABLE VIEW_EXTERNAL_USER VIEW_FIRM_DIRECTORY VIEW_USER_OFFICE",
	"Global Admin": "ASSIGN_EXTERNAL_USER_ROLES ASSIGN_INTERNAL_USER_ROLES BULK_DISABLE_FIRM_USERS CAN_MANAGE_DELEGATE_ENABLE_USER CAN_TRACK_DELEGATE_ACTIVATION_REQUESTS CONVERT_USER_TO_MULTI_FIRM CREATE_EXTERNAL_USER CREATE_LAA_APP_ROLE DELETE_AUDIT_USER DELETE_EXTERNAL_USER DISABLE_EXTERNAL_USER EDIT_EXTERNAL_USER EDIT_INTERNAL_USER EDIT_USER_DETAILS EDIT_USER_FIRM EDIT_USER_OFFICE ENABLE_EXTERNAL_USER EXPORT_AUDIT_DATA REMOVE_EXTERNAL_USER_ROLES REMOVE_INTERNAL_USER_ROLES RESEND_VERIFICATION_EMAIL UPDATE_USER_PROFILE_SILAS_STATUS VIEW_ALL_USER_MULTI_FIRM_PROFILES VIEW_AUDIT_TABLE VIEW_EXTERNAL_USER VIEW_FIRM_DIRECTORY VIEW_INTERNAL_USER VIEW_USER_OFFICE",
	"Firm User Manager": "ASSIGN_EXTERNAL_USER_ROLES CAN_REQUEST_DELEGATE_ENABLE_USER CAN_TRACK_DELEGATE_ACTIVATION_REQUESTS DELEGATE_EXTERNAL_USER_ACCESS DELETE_EXTERNAL_USER DISABLE_EXTERNAL_USER EDIT_EXTERNAL_USER EDIT_USER_OFFICE ENABLE_EXTERNAL_USER REMOVE_EXTERNAL_USER_ROLES VIEW_EXTERNAL_USER VIEW_USER_OFFICE",
	"Security Response": "BULK_DISABLE_FIRM_USERS CAN_MANAGE_DELEGATE_ENABLE_USER CAN_TRACK_DELEGATE_ACTIVATION_REQUESTS DELETE_AUDIT_USER DELETE_EXTERNAL_USER DISABLE_EXTERNAL_USER ENABLE_EXTERNAL_USER REMOVE_EXTERNAL_USER_ROLES REMOVE_INTERNAL_USER_ROLES VIEW_ALL_USER_MULTI_FIRM_PROFILES VIEW_AUDIT_TABLE VIEW_EXTERNAL_USER VIEW_FIRM_DIRECTORY VIEW_INTERNAL_USER VIEW_LAA_APP_METADATA VIEW_USER_OFFICE",
	"External User Support": "ASSIGN_EXTERNAL_USER_ROLES CAN_REQUEST_DELEGATE_ENABLE_USER CAN_TRACK_DELEGATE_ACTIVATION_REQUESTS CONVERT_USER_TO_MULTI_FIRM CREATE_LAA_APP_ROLE DELEGATE_EXTERNAL_USER_ACCESS_INTERNAL DELETE_EXTERNAL_USER DELETE_LAA_APP_ROLE DISABLE_EXTERNAL_USER EDIT_EXTERNAL_USER EDIT_LAA_APP_METADATA EDIT_LAA_APP_ROLE_ASSIGN_RESTRICTIONS EDIT_USER_DETAILS EDIT_USER_FIRM EDIT_USER_OFFICE REMOVE_EXTERNAL_USER_ROLES TRIGGER_CCMS_ROLE_SYNC TRIGGER_LAA_APP_SYNC VIEW_AUDIT_TABLE VIEW_EXTERNAL_USER VIEW_FIRM_DIRECTORY VIEW_INTERNAL_USER VIEW_LAA_APP_METADATA",
}
