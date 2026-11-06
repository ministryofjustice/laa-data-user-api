package authz

import future.keywords.if

test_same_firm_external_manager_can_deactivate if {
	allow with input as {
		"action": "deactivate",
		"actor": {
			"entra_oid": "actor-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"silas_role_identifiers": ["External User Manager"],
			"permissions": ["DISABLE_EXTERNAL_USER"],
		},
		"target": {
			"entra_oid": "target-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"multi_firm": false,
			"active": true,
			"deactivation_type": null,
		},
	}
}

test_external_actor_cannot_deactivate_across_firms if {
	not allow with input as {
		"action": "deactivate",
		"actor": {
			"entra_oid": "actor-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"silas_role_identifiers": ["External User Manager"],
			"permissions": ["DISABLE_EXTERNAL_USER"],
		},
		"target": {
			"entra_oid": "target-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-b",
			"multi_firm": false,
			"active": true,
			"deactivation_type": null,
		},
	}
}

test_actor_cannot_deactivate_self if {
	not allow with input as {
		"action": "deactivate",
		"actor": {
			"entra_oid": "ACTOR-ID",
			"user_type": "INTERNAL",
			"firm_id": null,
			"silas_role_identifiers": ["Global Admin"],
			"permissions": ["DISABLE_EXTERNAL_USER"],
		},
		"target": {
			"entra_oid": "actor-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"multi_firm": false,
			"active": true,
			"deactivation_type": null,
		},
	}
}

test_deactivation_requires_permission if {
	not allow with input as {
		"action": "deactivate",
		"actor": {
			"entra_oid": "actor-id",
			"user_type": "INTERNAL",
			"firm_id": null,
			"silas_role_identifiers": ["Global Admin"],
			"permissions": [],
		},
		"target": {
			"entra_oid": "target-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"multi_firm": false,
			"active": true,
			"deactivation_type": null,
		},
	}
}

test_internal_actor_can_reactivate_firm_deactivation if {
	allow with input as {
		"action": "reactivate",
		"actor": {
			"entra_oid": "actor-id",
			"user_type": "INTERNAL",
			"firm_id": null,
			"silas_role_identifiers": ["External User Admin"],
			"permissions": ["ENABLE_EXTERNAL_USER"],
		},
		"target": {
			"entra_oid": "target-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"multi_firm": false,
			"active": false,
			"deactivation_type": "FIRM",
		},
	}
}

test_firm_manager_cannot_reactivate_privileged_deactivation if {
	not allow with input as {
		"action": "reactivate",
		"actor": {
			"entra_oid": "actor-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"silas_role_identifiers": ["Firm User Manager"],
			"permissions": ["ENABLE_EXTERNAL_USER"],
		},
		"target": {
			"entra_oid": "target-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"multi_firm": false,
			"active": false,
			"deactivation_type": "PRIVILEGED",
		},
	}
}

test_firm_manager_can_reactivate_firm_deactivation_in_same_firm if {
	allow with input as {
		"action": "reactivate",
		"actor": {
			"entra_oid": "actor-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"silas_role_identifiers": ["Firm User Manager"],
			"permissions": ["ENABLE_EXTERNAL_USER"],
		},
		"target": {
			"entra_oid": "target-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"multi_firm": false,
			"active": false,
			"deactivation_type": "FIRM",
		},
	}
}

test_firm_manager_cannot_reactivate_sync_deactivation if {
	not allow with input as {
		"action": "reactivate",
		"actor": {
			"entra_oid": "actor-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"silas_role_identifiers": ["Firm User Manager"],
			"permissions": ["ENABLE_EXTERNAL_USER"],
		},
		"target": {
			"entra_oid": "target-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"multi_firm": false,
			"active": false,
			"deactivation_type": "SYNC",
		},
	}
}

test_legacy_deactivation_requires_internal_management_role if {
	not allow with input as {
		"action": "reactivate",
		"actor": {
			"entra_oid": "actor-id",
			"user_type": "INTERNAL",
			"firm_id": null,
			"silas_role_identifiers": ["Firm User Manager"],
			"permissions": ["ENABLE_EXTERNAL_USER"],
		},
		"target": {
			"entra_oid": "target-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"multi_firm": false,
			"active": false,
			"deactivation_type": null,
		},
	}
}

test_reactivation_denied_when_target_is_active if {
	not allow with input as {
		"action": "reactivate",
		"actor": {
			"entra_oid": "actor-id",
			"user_type": "INTERNAL",
			"firm_id": null,
			"silas_role_identifiers": ["Global Admin"],
			"permissions": ["ENABLE_EXTERNAL_USER"],
		},
		"target": {
			"entra_oid": "target-id",
			"user_type": "EXTERNAL",
			"firm_id": "firm-a",
			"multi_firm": false,
			"active": true,
			"deactivation_type": "NONE",
		},
	}
}
