INSERT INTO entra_user (
  id, entra_oid, first_name, last_name, email, status, multi_firm_user,
  mail_only, enabled, ccms_ebs_user, created_date, created_by
) VALUES (
  :'oid', :'oid', 'ZAP', 'Scan', 'zap-scan@example.invalid',
  'ACTIVE', false, false, true, false, CURRENT_TIMESTAMP, 'zap-active-scan'
);
INSERT INTO user_profile (
  id, entra_user_id, user_type, active_profile, status, silas_status,
  created_date, created_by
) VALUES (
  :'oid', :'oid', 'INTERNAL', true, 'COMPLETE', 'COMPLETE',
  CURRENT_TIMESTAMP, 'zap-active-scan'
);
INSERT INTO user_profile_app_role (user_profile_id, app_role_id)
SELECT :'oid', id FROM app_role
WHERE role_identifier IN ('Internal User Viewer', 'External User Viewer')
   OR (:'scan_mode' = 'write' AND role_identifier = 'Global Admin');
INSERT INTO firm (id, name, code, type, enabled)
VALUES ('00000000-0000-0000-0000-000000000002', 'ZAP Scan Firm',
        :'firm_code', 'ADVOCATE', true);
INSERT INTO office (id, firm_id, code, address_line_1, city, post_code)
VALUES ('00000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000002', '900002',
        '1 Test Street', 'Test City', 'TE1 1ST');
INSERT INTO user_account_status_audit (
  id, entra_user_id, status_changed_date, status_changed_by,
  status_change, user_email, user_name, disable_user_reason_id
) VALUES (
  '00000000-0000-0000-0000-000000000004', :'oid',
  CURRENT_TIMESTAMP - INTERVAL '1 day', 'zap-active-scan', 'DEACTIVATED',
  'zap-scan@example.invalid', 'ZAP Scan',
  (SELECT id FROM disable_user_reason WHERE name = 'Absence')
);

-- Writes target a different account so scanning cannot invalidate the actor.
INSERT INTO entra_user (
  id, entra_oid, first_name, last_name, email, status, multi_firm_user,
  mail_only, enabled, ccms_ebs_user, created_date, created_by
) VALUES (
  '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000006',
  'ZAP', 'Target', 'zap-target@example.invalid', 'ACTIVE', false,
  false, true, false, CURRENT_TIMESTAMP, 'zap-active-scan'
);
INSERT INTO user_profile (
  id, entra_user_id, user_type, active_profile, status, silas_status, firm_id,
  created_date, created_by
) VALUES (
  '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000006',
  'EXTERNAL', true, 'COMPLETE', 'COMPLETE', '00000000-0000-0000-0000-000000000002',
  CURRENT_TIMESTAMP, 'zap-active-scan'
);
