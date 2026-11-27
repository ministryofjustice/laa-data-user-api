package authz

import future.keywords.contains
import future.keywords.every
import future.keywords.if
import future.keywords.in

# Business-readable denial codes are returned to the API's decision log, never its clients.
trial := {"allow": false, "reason": trial_denial, "queryFilters": {"firmId": null, "userTypes": [], "roleIds": []}} if {
	trial_denial != "ALLOWED"
}

trial := {"allow": true, "reason": "ALLOWED", "queryFilters": {
	"firmId": trial_firm,
	"userTypes": sort(trial_filter_types),
	"roleIds": sort(trial_role_ids),
}} if {
	trial_denial == "ALLOWED"
}

trial_permissions := {permission |
	role := input.actor.roles[_]
	application := data.authz.permissions.role_permissions[_]
	permission := application[role][_]
}

trial_types contains "INTERNAL" if {
	input.actor.userType == "INTERNAL"
	"VIEW_INTERNAL_USER" in trial_permissions
}

trial_types contains "EXTERNAL" if {
	"VIEW_EXTERNAL_USER" in trial_permissions
}

trial_filter_types := trial_types if {
	input.operation in {"SEARCH", "DETAIL"}
} else := {input.target.userType}

trial_firm := input.actor.firmId if {
	input.actor.userType == "EXTERNAL"
} else := null

trial_firm_ok if {
	input.actor.userType == "INTERNAL"
}

trial_firm_ok if {
	input.actor.userType == "EXTERNAL"
	input.target.userType == "EXTERNAL"
	input.actor.firmId != null
	input.target.firmId == input.actor.firmId
}

trial_compatible(role) if {
	role.enabled == true
	count(role.userTypes) == 0
	trial_firm_type_ok(role)
}

trial_compatible(role) if {
	role.enabled == true
	input.target.userType in role.userTypes
	trial_firm_type_ok(role)
}

trial_firm_type_ok(role) if {
	count(role.firmTypes) == 0
}

trial_firm_type_ok(role) if {
	input.target.firmType in role.firmTypes
}

trial_assignable(role) if {
	count(role.assigners) == 0
}

trial_assignable(role) if {
	assigner := role.assigners[_]
	assigner in input.actor.roleIds
}

trial_role_ids := {id |
	input.operation == "ASSIGNABLE"
	role := input.roles[id]
	trial_compatible(role)
	trial_assignable(role)
}

trial_assign_permission := "ASSIGN_INTERNAL_USER_ROLES" if {
	input.target.userType == "INTERNAL"
} else := "ASSIGN_EXTERNAL_USER_ROLES"

trial_remove_permission := "REMOVE_INTERNAL_USER_ROLES" if {
	input.target.userType == "INTERNAL"
} else := "REMOVE_EXTERNAL_USER_ROLES"

trial_change_permissions if {
	count(input.additions) == 0
	count(input.removals) == 0
}

trial_change_permissions if {
	count(input.additions) > 0
	trial_assign_permission in trial_permissions
	count(input.removals) == 0
}

trial_change_permissions if {
	count(input.additions) == 0
	count(input.removals) > 0
	trial_remove_permission in trial_permissions
}

trial_change_permissions if {
	count(input.additions) > 0
	count(input.removals) > 0
	trial_assign_permission in trial_permissions
	trial_remove_permission in trial_permissions
}

trial_denial := "ACTOR_INACTIVE" if {
	not input.actor.active
} else := "ACTOR_INACTIVE" if {
	not input.actor.userType in {"INTERNAL", "EXTERNAL"}
} else := "INVALID_REQUEST" if {
	input.validRequest != true
} else := "INVALID_REQUEST" if {
	not input.operation in {"SEARCH", "DETAIL", "ASSIGNABLE", "ASSIGN", "UPDATE"}
} else := "FIRM_SCOPE" if {
	input.actor.userType == "EXTERNAL"
	input.actor.firmId == null
} else := "MISSING_PERMISSION" if {
	input.operation == "SEARCH"
	count(trial_types) == 0
} else := "TARGET_NOT_FOUND" if {
	input.operation != "SEARCH"
	input.target == null
} else := "FIRM_SCOPE" if {
	input.operation != "SEARCH"
	not trial_firm_ok
} else := "MISSING_PERMISSION" if {
	input.operation == "DETAIL"
	not input.target.userType in trial_types
} else := "MISSING_PERMISSION" if {
	input.operation == "ASSIGNABLE"
	not trial_assign_permission in trial_permissions
} else := "MISSING_PERMISSION" if {
	input.operation in {"ASSIGN", "UPDATE"}
	not trial_change_permissions
} else := "INCOMPATIBLE_ROLE" if {
	input.operation in {"ASSIGN", "UPDATE"}
	some id in input.additions
	not trial_compatible(input.roles[id])
} else := "ASSIGNMENT_RESTRICTED" if {
	input.operation in {"ASSIGN", "UPDATE"}
	some id in input.additions
	not trial_assignable(input.roles[id])
} else := "ALLOWED"
