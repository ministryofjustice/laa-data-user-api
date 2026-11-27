package authz_test

import data.authz.trial
import future.keywords.if

trial_input := {
	"operation": "SEARCH",
	"actor": {"active": true, "userType": "EXTERNAL", "firmId": "firm-a", "roles": ["Firm User Manager"], "roleIds": ["manager"]},
	"target": {"id": "target", "userType": "EXTERNAL", "firmId": "firm-a", "firmType": "LEGAL_SERVICES_PROVIDER"},
	"roles": {
		"role-a": {"enabled": true, "userTypes": ["EXTERNAL"], "firmTypes": [], "assigners": ["manager"]},
		"role-b": {"enabled": true, "userTypes": ["INTERNAL"], "firmTypes": [], "assigners": []},
		"role-c": {"enabled": true, "userTypes": [], "firmTypes": [], "assigners": ["other"]},
	},
	"validRequest": true,
	"additions": ["role-a"],
	"removals": [],
}

test_trial_search_constrains_firm_and_user_type if {
	result := trial with input as trial_input
	result.allow
	result.queryFilters == {"firmId": "firm-a", "userTypes": ["EXTERNAL"], "roleIds": []}
}

test_trial_different_firm_is_denied if {
	target := object.union(trial_input.target, {"firmId": "firm-b"})
	result := trial with input as object.union(trial_input, {"operation": "DETAIL", "target": target})
	not result.allow
	result.reason == "FIRM_SCOPE"
}

test_trial_missing_target_is_denied if {
	result := trial with input as object.union(trial_input, {"operation": "DETAIL", "target": null})
	not result.allow
	result.reason == "TARGET_NOT_FOUND"
}

test_trial_external_caller_cannot_target_an_internal_profile_even_with_matching_firm if {
	target := object.union(trial_input.target, {"userType": "INTERNAL"})
	result := trial with input as object.union(trial_input, {"operation": "ASSIGN", "target": target})
	not result.allow
	result.reason == "FIRM_SCOPE"
}

test_trial_assignable_roles_obey_both_restriction_types if {
	result := trial with input as object.union(trial_input, {"operation": "ASSIGNABLE"})
	result.allow
	result.queryFilters.roleIds == ["role-a"]
}

test_trial_allowed_role_is_assignable if {
	result := trial with input as object.union(trial_input, {"operation": "ASSIGN"})
	result.allow
}

test_trial_incompatible_role_is_denied if {
	result := trial with input as object.union(trial_input, {"operation": "ASSIGN", "additions": ["role-b"]})
	not result.allow
	result.reason == "INCOMPATIBLE_ROLE"
}

test_trial_assigner_restriction_is_enforced if {
	result := trial with input as object.union(trial_input, {"operation": "ASSIGN", "additions": ["role-c"]})
	not result.allow
	result.reason == "ASSIGNMENT_RESTRICTED"
}

test_trial_inactive_actor_is_denied if {
	actor := object.union(trial_input.actor, {"active": false})
	result := trial with input as object.union(trial_input, {"actor": actor})
	not result.allow
	result.reason == "ACTOR_INACTIVE"
}

test_trial_unknown_role_grants_nothing if {
	actor := object.union(trial_input.actor, {"roles": ["Unknown"]})
	result := trial with input as object.union(trial_input, {"actor": actor})
	not result.allow
	result.reason == "MISSING_PERMISSION"
}

test_trial_invalid_request_is_denied if {
	result := trial with input as object.union(trial_input, {"validRequest": false})
	not result.allow
	result.reason == "INVALID_REQUEST"
}

test_trial_removals_do_not_require_addition_compatibility if {
	result := trial with input as object.union(trial_input, {"operation": "UPDATE", "additions": [], "removals": ["role-b"]})
	result.allow
}
