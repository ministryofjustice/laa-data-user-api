--
-- PostgreSQL database dump
--

\restrict oLUbbXjtDeD9xw74pUsSWOBZzQ9QcAcDc0ETxnZlqYPzCTBeAfky0oXa3AFf86V

-- Dumped from database version 16.13
-- Dumped by pg_dump version 18.4 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: firm_type_enum; Type: TYPE; Schema: public; Owner: cptJXFyh0U
--

CREATE TYPE public.firm_type_enum AS ENUM (
    'LEGAL_SERVICES_PROVIDER',
    'CHAMBERS',
    'ADVOCATE'
);


ALTER TYPE public.firm_type_enum OWNER TO "cptJXFyh0U";

--
-- Name: check_firms_have_office(); Type: FUNCTION; Schema: public; Owner: cptJXFyh0U
--

CREATE FUNCTION public.check_firms_have_office() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ 
BEGIN 
  /* Skip check if session variable is set (for PDA sync) */
  IF current_setting('app.internal.pda_sync_bypass_constraint_check', true) = 'true' THEN
    IF TG_OP = 'DELETE' THEN 
      RETURN OLD; 
    ELSE 
      RETURN NEW; 
    END IF; 
  END IF;
  
  /* Normal validation */
  IF EXISTS ( 
    SELECT 1 
    FROM firm f 
    WHERE f.enabled = true 
    AND NOT EXISTS ( 
      SELECT 1 FROM office o 
      WHERE o.firm_id = f.id 
    ) 
  ) THEN 
    RAISE EXCEPTION 'Enabled firm must have at least one office'; 
  END IF; 
  IF TG_OP = 'DELETE' THEN 
    RETURN OLD; 
  ELSE 
    RETURN NEW; 
  END IF; 
END; 
$$;


ALTER FUNCTION public.check_firms_have_office() OWNER TO "cptJXFyh0U";

--
-- Name: check_parent_not_advocate(); Type: FUNCTION; Schema: public; Owner: cptJXFyh0U
--

CREATE FUNCTION public.check_parent_not_advocate() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ DECLARE parent_type VARCHAR(255); BEGIN IF NEW.parent_firm_id IS NULL THEN RETURN NEW; END IF; SELECT type INTO parent_type FROM firm WHERE id = NEW.parent_firm_id; IF parent_type = 'ADVOCATE' THEN RAISE EXCEPTION 'ADVOCATE firms cannot be parent firms (parent=%)', NEW.parent_firm_id; END IF; RETURN NEW; END; $$;


ALTER FUNCTION public.check_parent_not_advocate() OWNER TO "cptJXFyh0U";

--
-- Name: check_user_profile_office_matches_firm(); Type: FUNCTION; Schema: public; Owner: cptJXFyh0U
--

CREATE FUNCTION public.check_user_profile_office_matches_firm() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ DECLARE up_firm UUID; office_firm UUID; BEGIN SELECT firm_id INTO up_firm FROM user_profile WHERE id = NEW.user_profile_id; IF up_firm IS NULL THEN RETURN NEW; END IF; SELECT firm_id INTO office_firm FROM office WHERE id = NEW.office_id; IF office_firm IS NULL THEN RAISE EXCEPTION 'Office % has no firm assigned', NEW.office_id; END IF; IF office_firm <> up_firm THEN RAISE EXCEPTION 'Office % does not belong to the user_profile''s firm', NEW.office_id; END IF; RETURN NEW; END; $$;


ALTER FUNCTION public.check_user_profile_office_matches_firm() OWNER TO "cptJXFyh0U";

--
-- Name: is_child(); Type: FUNCTION; Schema: public; Owner: cptJXFyh0U
--

CREATE FUNCTION public.is_child() RETURNS trigger
    LANGUAGE plpgsql STABLE STRICT
    AS $$ BEGIN IF NEW.parent_firm_id IS NULL THEN RETURN NEW; END IF; IF ( SELECT EXISTS(SELECT 1 FROM firm AS f WHERE f.parent_firm_id is not null and f.id = NEW.parent_firm_id ) ) THEN RAISE EXCEPTION 'parent firm (%) already has parent', NEW.parent_firm_id; ELSE RETURN NEW; END IF; END;$$;


ALTER FUNCTION public.is_child() OWNER TO "cptJXFyh0U";

--
-- Name: prevent_advocate_with_children_on_type_change(); Type: FUNCTION; Schema: public; Owner: cptJXFyh0U
--

CREATE FUNCTION public.prevent_advocate_with_children_on_type_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN IF NEW.type = 'ADVOCATE' AND EXISTS ( SELECT 1 FROM firm f WHERE f.parent_firm_id = NEW.id ) THEN RAISE EXCEPTION 'Cannot set firm to ADVOCATE while it is a parent (id=%)', NEW.id; END IF; RETURN NEW; END; $$;


ALTER FUNCTION public.prevent_advocate_with_children_on_type_change() OWNER TO "cptJXFyh0U";

--
-- Name: prevent_internal_multi_firm_true(); Type: FUNCTION; Schema: public; Owner: cptJXFyh0U
--

CREATE FUNCTION public.prevent_internal_multi_firm_true() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN IF EXISTS ( SELECT 1 FROM user_profile up WHERE up.entra_user_id = NEW.id AND up.user_type = 'INTERNAL' ) THEN RAISE EXCEPTION 'Internal users cannot have multi_firm_user=true (entra_user_id=%)', NEW.id; END IF; RETURN NEW; END; $$;


ALTER FUNCTION public.prevent_internal_multi_firm_true() OWNER TO "cptJXFyh0U";

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.app (
    id uuid NOT NULL,
    entra_app_id character varying(255),
    name character varying(255) NOT NULL,
    security_group_oid character varying(255) NOT NULL,
    ordinal integer DEFAULT 0,
    enabled boolean DEFAULT true,
    description text NOT NULL,
    url character varying(255) NOT NULL,
    app_type character varying(255) NOT NULL,
    entra_oid character varying(255),
    CONSTRAINT app_app_type_check CHECK (((app_type)::text = ANY (ARRAY[('AUTHZ'::character varying)::text, ('LAA'::character varying)::text])))
);


ALTER TABLE public.app OWNER TO "cptJXFyh0U";

--
-- Name: app_role; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.app_role (
    authz_role boolean DEFAULT false NOT NULL,
    app_id uuid NOT NULL,
    id uuid NOT NULL,
    description character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    user_type_restriction text[],
    ccms_code character varying(30),
    legacy_sync boolean DEFAULT false NOT NULL,
    ordinal integer DEFAULT 0,
    firm_type_restriction public.firm_type_enum[]
);


ALTER TABLE public.app_role OWNER TO "cptJXFyh0U";

--
-- Name: databasechangelog; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);


ALTER TABLE public.databasechangelog OWNER TO "cptJXFyh0U";

--
-- Name: databasechangeloglock; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255)
);


ALTER TABLE public.databasechangeloglock OWNER TO "cptJXFyh0U";

--
-- Name: delete_user_reason; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.delete_user_reason (
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    editable_by_internal_user boolean DEFAULT false NOT NULL,
    editable_by_external_user boolean DEFAULT false NOT NULL,
    system_generated boolean DEFAULT false NOT NULL
);


ALTER TABLE public.delete_user_reason OWNER TO "cptJXFyh0U";

--
-- Name: disable_user_reason; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.disable_user_reason (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255) NOT NULL,
    entra_description character varying(255) NOT NULL,
    user_selectable boolean DEFAULT true NOT NULL
);


ALTER TABLE public.disable_user_reason OWNER TO "cptJXFyh0U";

--
-- Name: distributed_lock; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.distributed_lock (
    lock_key character varying(255) NOT NULL,
    locked_until timestamp without time zone NOT NULL,
    locked_by character varying(255) NOT NULL
);


ALTER TABLE public.distributed_lock OWNER TO "cptJXFyh0U";

--
-- Name: entra_last_sync_metadata; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.entra_last_sync_metadata (
    id character varying(50) DEFAULT 'ENTRA_USER_SYNC'::character varying NOT NULL,
    updated_at timestamp without time zone,
    last_successful_to timestamp without time zone
);


ALTER TABLE public.entra_last_sync_metadata OWNER TO "cptJXFyh0U";

--
-- Name: entra_user; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.entra_user (
    created_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    id uuid NOT NULL,
    created_by character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    entra_oid character varying(255) NOT NULL,
    first_name character varying(255) NOT NULL,
    last_modified_by character varying(255),
    last_name character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    firm_name character varying(255),
    multi_firm_user boolean DEFAULT false NOT NULL,
    last_synced_on timestamp without time zone,
    mail_only boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true,
    invitation_status character varying(255),
    disabled_by uuid,
    disable_type character varying(20),
    ccms_ebs_user boolean DEFAULT false NOT NULL,
    CONSTRAINT entra_user_invitation_status_check CHECK (((invitation_status)::text = ANY (ARRAY[('INVITE_SENT'::character varying)::text, ('AWAITING_MFA'::character varying)::text, ('AWAITING_VERIFICATION'::character varying)::text, ('VERIFICATION_SUCCESS'::character varying)::text, ('CODE_EXPIRED'::character varying)::text, ('MAX_VERIFICATION_ATTEMPTS'::character varying)::text, ('VERIFICATION_FAILED'::character varying)::text]))),
    CONSTRAINT entra_user_status_check CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('DEACTIVE'::character varying)::text, ('AWAITING_USER_APPROVAL'::character varying)::text])))
);


ALTER TABLE public.entra_user OWNER TO "cptJXFyh0U";

--
-- Name: COLUMN entra_user.multi_firm_user; Type: COMMENT; Schema: public; Owner: cptJXFyh0U
--

COMMENT ON COLUMN public.entra_user.multi_firm_user IS 'Flag indicating if user can access multiple firms without being tied to a specific firm initially';


--
-- Name: firm; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.firm (
    id uuid NOT NULL,
    code character varying(255),
    name character varying(255) NOT NULL,
    type public.firm_type_enum NOT NULL,
    parent_firm_id uuid,
    enabled boolean DEFAULT true NOT NULL,
    CONSTRAINT self_parent CHECK ((parent_firm_id <> id))
);


ALTER TABLE public.firm OWNER TO "cptJXFyh0U";

--
-- Name: oauth2_authorized_client; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.oauth2_authorized_client (
    client_registration_id character varying(100) NOT NULL,
    principal_name character varying(200) NOT NULL,
    access_token_type character varying(100) NOT NULL,
    access_token_value bytea NOT NULL,
    access_token_issued_at timestamp without time zone NOT NULL,
    access_token_expires_at timestamp without time zone NOT NULL,
    access_token_scopes character varying(1000),
    refresh_token_value bytea,
    refresh_token_issued_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.oauth2_authorized_client OWNER TO "cptJXFyh0U";

--
-- Name: office; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.office (
    firm_id uuid NOT NULL,
    id uuid NOT NULL,
    code character varying(255),
    post_code character varying(20),
    address_line_1 character varying(255),
    address_line_2 character varying(255),
    city character varying(255),
    address_line_3 character varying(255)
);


ALTER TABLE public.office OWNER TO "cptJXFyh0U";

--
-- Name: role_assignment; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.role_assignment (
    assignable_role_id uuid NOT NULL,
    assigning_role_id uuid NOT NULL
);


ALTER TABLE public.role_assignment OWNER TO "cptJXFyh0U";

--
-- Name: role_permission; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.role_permission (
    app_role_id uuid NOT NULL,
    permission character varying(255) NOT NULL
);


ALTER TABLE public.role_permission OWNER TO "cptJXFyh0U";

--
-- Name: spring_session; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.spring_session (
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100)
);


ALTER TABLE public.spring_session OWNER TO "cptJXFyh0U";

--
-- Name: spring_session_attributes; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.spring_session_attributes (
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL
);


ALTER TABLE public.spring_session_attributes OWNER TO "cptJXFyh0U";

--
-- Name: user_account_status_audit; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.user_account_status_audit (
    id uuid NOT NULL,
    entra_user_id uuid,
    disable_user_reason_id uuid,
    status_changed_date timestamp without time zone NOT NULL,
    status_change character varying(255) NOT NULL,
    status_changed_by character varying(255) NOT NULL,
    disable_type character varying(20),
    user_email character varying(255),
    user_name character varying(511),
    delete_user_reason_id uuid,
    CONSTRAINT user_account_status_audit_status_change_check CHECK (((status_change)::text = ANY (ARRAY[('ENABLED'::character varying)::text, ('DISABLED'::character varying)::text, ('DELETED'::character varying)::text]))),
    CONSTRAINT user_account_status_audit_user_reference_check CHECK (((entra_user_id IS NOT NULL) OR (user_email IS NOT NULL)))
);


ALTER TABLE public.user_account_status_audit OWNER TO "cptJXFyh0U";

--
-- Name: user_profile; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.user_profile (
    active_profile boolean NOT NULL,
    created_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    entra_user_id uuid NOT NULL,
    firm_id uuid,
    id uuid NOT NULL,
    legacy_user_id uuid,
    created_by character varying(255) NOT NULL,
    last_modified_by character varying(255),
    status character varying(255) DEFAULT 'PENDING'::character varying NOT NULL,
    user_type character varying(255) NOT NULL,
    last_sync_successful boolean DEFAULT false NOT NULL,
    unrestricted_office_access boolean DEFAULT false NOT NULL,
    silas_status character varying(255) DEFAULT 'UNKNOWN'::character varying NOT NULL,
    CONSTRAINT firm_not_null_for_non_internal_users_only CHECK ((((firm_id IS NULL) AND ((user_type)::text = 'INTERNAL'::text)) OR ((firm_id IS NOT NULL) AND ((user_type)::text <> 'INTERNAL'::text)))),
    CONSTRAINT user_profile_silas_status_check CHECK (((silas_status)::text = ANY (ARRAY['COMPLETE'::text, 'INCOMPLETE'::text, 'ACTIVATION_PENDING'::text, 'NO_ROLES_ASSIGNED'::text, 'DISABLED'::text, 'UNKNOWN'::text]))),
    CONSTRAINT user_profile_status_check CHECK (((status)::text = ANY (ARRAY[('COMPLETE'::character varying)::text, ('PENDING'::character varying)::text]))),
    CONSTRAINT user_profile_user_type_check CHECK (((user_type)::text = ANY (ARRAY[('INTERNAL'::character varying)::text, ('EXTERNAL'::character varying)::text])))
);


ALTER TABLE public.user_profile OWNER TO "cptJXFyh0U";

--
-- Name: user_profile_app_role; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.user_profile_app_role (
    app_role_id uuid NOT NULL,
    user_profile_id uuid NOT NULL
);


ALTER TABLE public.user_profile_app_role OWNER TO "cptJXFyh0U";

--
-- Name: user_profile_office; Type: TABLE; Schema: public; Owner: cptJXFyh0U
--

CREATE TABLE public.user_profile_office (
    office_id uuid NOT NULL,
    user_profile_id uuid NOT NULL
);


ALTER TABLE public.user_profile_office OWNER TO "cptJXFyh0U";

--
-- Name: app app_entra_app_id_key; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.app
    ADD CONSTRAINT app_entra_app_id_key UNIQUE (entra_app_id);


--
-- Name: app app_entra_oid_unique_key; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.app
    ADD CONSTRAINT app_entra_oid_unique_key UNIQUE (entra_oid);


--
-- Name: app app_name_key; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.app
    ADD CONSTRAINT app_name_key UNIQUE (name);


--
-- Name: app app_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.app
    ADD CONSTRAINT app_pkey PRIMARY KEY (id);


--
-- Name: app_role app_role_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.app_role
    ADD CONSTRAINT app_role_pkey PRIMARY KEY (id);


--
-- Name: app app_security_group_oid_key; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.app
    ADD CONSTRAINT app_security_group_oid_key UNIQUE (security_group_oid);


--
-- Name: databasechangeloglock databasechangeloglock_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.databasechangeloglock
    ADD CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (id);


--
-- Name: delete_user_reason delete_user_reason_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.delete_user_reason
    ADD CONSTRAINT delete_user_reason_pkey PRIMARY KEY (id);


--
-- Name: disable_user_reason disable_user_reason_entra_description_unique; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.disable_user_reason
    ADD CONSTRAINT disable_user_reason_entra_description_unique UNIQUE (entra_description);


--
-- Name: disable_user_reason disable_user_reason_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.disable_user_reason
    ADD CONSTRAINT disable_user_reason_pkey PRIMARY KEY (id);


--
-- Name: distributed_lock distributed_lock_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.distributed_lock
    ADD CONSTRAINT distributed_lock_pkey PRIMARY KEY (lock_key);


--
-- Name: entra_last_sync_metadata entra_last_sync_metadata_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.entra_last_sync_metadata
    ADD CONSTRAINT entra_last_sync_metadata_pkey PRIMARY KEY (id);


--
-- Name: entra_user entra_user_email_key; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.entra_user
    ADD CONSTRAINT entra_user_email_key UNIQUE (email);


--
-- Name: entra_user entra_user_entra_oid_key; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.entra_user
    ADD CONSTRAINT entra_user_entra_oid_key UNIQUE (entra_oid);


--
-- Name: entra_user entra_user_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.entra_user
    ADD CONSTRAINT entra_user_pkey PRIMARY KEY (id);


--
-- Name: firm firm_code_key; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.firm
    ADD CONSTRAINT firm_code_key UNIQUE (code);


--
-- Name: firm firm_name_key; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.firm
    ADD CONSTRAINT firm_name_key UNIQUE (name);


--
-- Name: firm firm_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.firm
    ADD CONSTRAINT firm_pkey PRIMARY KEY (id);


--
-- Name: oauth2_authorized_client oauth2_authorized_client_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.oauth2_authorized_client
    ADD CONSTRAINT oauth2_authorized_client_pkey PRIMARY KEY (client_registration_id, principal_name);


--
-- Name: office office_code_key; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.office
    ADD CONSTRAINT office_code_key UNIQUE (code);


--
-- Name: office office_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.office
    ADD CONSTRAINT office_pkey PRIMARY KEY (id);


--
-- Name: role_assignment role_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT role_assignment_pkey PRIMARY KEY (assignable_role_id, assigning_role_id);


--
-- Name: spring_session_attributes spring_session_attributes_pk; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name);


--
-- Name: spring_session spring_session_pk; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.spring_session
    ADD CONSTRAINT spring_session_pk PRIMARY KEY (primary_id);


--
-- Name: app uq_app_description; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.app
    ADD CONSTRAINT uq_app_description UNIQUE (description);


--
-- Name: delete_user_reason uq_delete_user_reason_code; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.delete_user_reason
    ADD CONSTRAINT uq_delete_user_reason_code UNIQUE (code);


--
-- Name: user_account_status_audit user_account_status_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_account_status_audit
    ADD CONSTRAINT user_account_status_audit_pkey PRIMARY KEY (id);


--
-- Name: user_profile_app_role user_profile_app_role_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_profile_app_role
    ADD CONSTRAINT user_profile_app_role_pkey PRIMARY KEY (app_role_id, user_profile_id);


--
-- Name: user_profile_office user_profile_office_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_profile_office
    ADD CONSTRAINT user_profile_office_pkey PRIMARY KEY (office_id, user_profile_id);


--
-- Name: user_profile user_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_profile
    ADD CONSTRAINT user_profile_pkey PRIMARY KEY (id);


--
-- Name: app_role_app_id_ccms_code_user_type_idx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE UNIQUE INDEX app_role_app_id_ccms_code_user_type_idx ON public.app_role USING btree (app_id, ccms_code, user_type_restriction) WHERE (ccms_code IS NOT NULL);


--
-- Name: app_role_name_legacy_sync_idx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE UNIQUE INDEX app_role_name_legacy_sync_idx ON public.app_role USING btree (name, user_type_restriction) WHERE (legacy_sync = false);


--
-- Name: idx_app_role_authz_name; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_app_role_authz_name ON public.app_role USING btree (authz_role, name);


--
-- Name: idx_distributed_lock_locked_until; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_distributed_lock_locked_until ON public.distributed_lock USING btree (locked_until);


--
-- Name: idx_entra_user_email_lower; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_entra_user_email_lower ON public.entra_user USING btree (lower((email)::text));


--
-- Name: idx_entra_user_first_name_lower; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_entra_user_first_name_lower ON public.entra_user USING btree (lower((first_name)::text));


--
-- Name: idx_entra_user_fullname_lower; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_entra_user_fullname_lower ON public.entra_user USING btree (lower((((first_name)::text || ' '::text) || (last_name)::text)));


--
-- Name: idx_entra_user_last_name_lower; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_entra_user_last_name_lower ON public.entra_user USING btree (lower((last_name)::text));


--
-- Name: idx_entra_user_multi_firm; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_entra_user_multi_firm ON public.entra_user USING btree (multi_firm_user);


--
-- Name: idx_firm_code; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_firm_code ON public.firm USING btree (code) WHERE (code IS NOT NULL);


--
-- Name: idx_firm_enabled; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_firm_enabled ON public.firm USING btree (enabled);


--
-- Name: idx_firm_name; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_firm_name ON public.firm USING btree (name) WHERE (name IS NOT NULL);


--
-- Name: idx_firm_parent_firm_id; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_firm_parent_firm_id ON public.firm USING btree (parent_firm_id) WHERE (parent_firm_id IS NOT NULL);


--
-- Name: idx_office_code; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_office_code ON public.office USING btree (code) WHERE (code IS NOT NULL);


--
-- Name: idx_office_firm_id; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_office_firm_id ON public.office USING btree (firm_id) WHERE (firm_id IS NOT NULL);


--
-- Name: idx_office_firm_id_code; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_office_firm_id_code ON public.office USING btree (firm_id, code) WHERE ((firm_id IS NOT NULL) AND (code IS NOT NULL));


--
-- Name: idx_user_profile_entra_user_id; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_user_profile_entra_user_id ON public.user_profile USING btree (entra_user_id);


--
-- Name: idx_user_profile_firm_id; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_user_profile_firm_id ON public.user_profile USING btree (firm_id) WHERE (firm_id IS NOT NULL);


--
-- Name: idx_user_profile_firm_user_type; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_user_profile_firm_user_type ON public.user_profile USING btree (firm_id, user_type);


--
-- Name: idx_user_profile_office_office_id; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_user_profile_office_office_id ON public.user_profile_office USING btree (office_id) WHERE (office_id IS NOT NULL);


--
-- Name: idx_user_profile_user_type; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_user_profile_user_type ON public.user_profile USING btree (user_type);


--
-- Name: idx_userprofile_firm_id; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX idx_userprofile_firm_id ON public.user_profile USING btree (firm_id);


--
-- Name: one_active_profile_per_user; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE UNIQUE INDEX one_active_profile_per_user ON public.user_profile USING btree (entra_user_id) WHERE (active_profile = true);


--
-- Name: one_profile_per_firm_for_external_user; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE UNIQUE INDEX one_profile_per_firm_for_external_user ON public.user_profile USING btree (entra_user_id, firm_id) WHERE ((user_type)::text = 'EXTERNAL'::text);


--
-- Name: one_profile_per_internal_user; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE UNIQUE INDEX one_profile_per_internal_user ON public.user_profile USING btree (entra_user_id) WHERE ((user_type)::text = 'INTERNAL'::text);


--
-- Name: spring_session_ix1; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE UNIQUE INDEX spring_session_ix1 ON public.spring_session USING btree (session_id);


--
-- Name: spring_session_ix2; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX spring_session_ix2 ON public.spring_session USING btree (expiry_time);


--
-- Name: spring_session_ix3; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX spring_session_ix3 ON public.spring_session USING btree (principal_name);


--
-- Name: uix_external_profile_per_firm; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE UNIQUE INDEX uix_external_profile_per_firm ON public.user_profile USING btree (entra_user_id, firm_id) WHERE ((user_type)::text = 'EXTERNAL'::text);


--
-- Name: usercreatedbyidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX usercreatedbyidx ON public.entra_user USING btree (created_by);


--
-- Name: usercreateddateidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX usercreateddateidx ON public.entra_user USING btree (created_date);


--
-- Name: userfirstnameidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX userfirstnameidx ON public.entra_user USING btree (first_name);


--
-- Name: userlastmodifiedbyidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX userlastmodifiedbyidx ON public.entra_user USING btree (last_modified_by);


--
-- Name: userlastmodifieddateidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX userlastmodifieddateidx ON public.entra_user USING btree (last_modified_date);


--
-- Name: userlastnameidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX userlastnameidx ON public.entra_user USING btree (last_name);


--
-- Name: userprofilecreatedbyidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX userprofilecreatedbyidx ON public.user_profile USING btree (created_by);


--
-- Name: userprofilecreateddateidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX userprofilecreateddateidx ON public.user_profile USING btree (created_date);


--
-- Name: userprofilelastmodifiedbyidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX userprofilelastmodifiedbyidx ON public.user_profile USING btree (last_modified_by);


--
-- Name: userprofilelastmodifieddateidx; Type: INDEX; Schema: public; Owner: cptJXFyh0U
--

CREATE INDEX userprofilelastmodifieddateidx ON public.user_profile USING btree (last_modified_date);


--
-- Name: firm trg_advocate_type_change_for_parent; Type: TRIGGER; Schema: public; Owner: cptJXFyh0U
--

CREATE TRIGGER trg_advocate_type_change_for_parent BEFORE UPDATE OF type ON public.firm FOR EACH ROW EXECUTE FUNCTION public.prevent_advocate_with_children_on_type_change();


--
-- Name: firm trg_check_firm_offices_on_firm; Type: TRIGGER; Schema: public; Owner: cptJXFyh0U
--

CREATE CONSTRAINT TRIGGER trg_check_firm_offices_on_firm AFTER INSERT OR DELETE OR UPDATE ON public.firm DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.check_firms_have_office();


--
-- Name: office trg_check_firm_offices_on_office; Type: TRIGGER; Schema: public; Owner: cptJXFyh0U
--

CREATE CONSTRAINT TRIGGER trg_check_firm_offices_on_office AFTER INSERT OR DELETE OR UPDATE ON public.office DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.check_firms_have_office();


--
-- Name: firm trg_parent_not_advocate; Type: TRIGGER; Schema: public; Owner: cptJXFyh0U
--

CREATE TRIGGER trg_parent_not_advocate BEFORE INSERT OR UPDATE OF parent_firm_id ON public.firm FOR EACH ROW EXECUTE FUNCTION public.check_parent_not_advocate();


--
-- Name: entra_user trg_prevent_internal_multi_firm_true; Type: TRIGGER; Schema: public; Owner: cptJXFyh0U
--

CREATE TRIGGER trg_prevent_internal_multi_firm_true BEFORE INSERT OR UPDATE OF multi_firm_user ON public.entra_user FOR EACH ROW WHEN ((new.multi_firm_user IS TRUE)) EXECUTE FUNCTION public.prevent_internal_multi_firm_true();


--
-- Name: user_profile_office trg_user_profile_office_matches_firm; Type: TRIGGER; Schema: public; Owner: cptJXFyh0U
--

CREATE TRIGGER trg_user_profile_office_matches_firm BEFORE INSERT OR UPDATE OF office_id, user_profile_id ON public.user_profile_office FOR EACH ROW EXECUTE FUNCTION public.check_user_profile_office_matches_firm();


--
-- Name: firm valid_grandparent; Type: TRIGGER; Schema: public; Owner: cptJXFyh0U
--

CREATE TRIGGER valid_grandparent BEFORE INSERT OR UPDATE ON public.firm FOR EACH ROW EXECUTE FUNCTION public.is_child();


--
-- Name: firm FK_parent_firm_firm_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.firm
    ADD CONSTRAINT "FK_parent_firm_firm_id" FOREIGN KEY (parent_firm_id) REFERENCES public.firm(id);


--
-- Name: app_role fk_app_role_app_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.app_role
    ADD CONSTRAINT fk_app_role_app_id FOREIGN KEY (app_id) REFERENCES public.app(id);


--
-- Name: office fk_office_firm_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.office
    ADD CONSTRAINT fk_office_firm_id FOREIGN KEY (firm_id) REFERENCES public.firm(id);


--
-- Name: role_permission fk_permission_app_role_app_role_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT fk_permission_app_role_app_role_id FOREIGN KEY (app_role_id) REFERENCES public.app_role(id);


--
-- Name: user_account_status_audit fk_user_account_status_audit_delete_user_reason_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_account_status_audit
    ADD CONSTRAINT fk_user_account_status_audit_delete_user_reason_id FOREIGN KEY (delete_user_reason_id) REFERENCES public.delete_user_reason(id);


--
-- Name: user_account_status_audit fk_user_account_status_audit_disable_user_reason_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_account_status_audit
    ADD CONSTRAINT fk_user_account_status_audit_disable_user_reason_id FOREIGN KEY (disable_user_reason_id) REFERENCES public.disable_user_reason(id);


--
-- Name: user_account_status_audit fk_user_account_status_audit_entra_user_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_account_status_audit
    ADD CONSTRAINT fk_user_account_status_audit_entra_user_id FOREIGN KEY (entra_user_id) REFERENCES public.entra_user(id);


--
-- Name: user_profile_app_role fk_user_profile_app_role_app_role_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_profile_app_role
    ADD CONSTRAINT fk_user_profile_app_role_app_role_id FOREIGN KEY (app_role_id) REFERENCES public.app_role(id);


--
-- Name: user_profile_app_role fk_user_profile_app_role_user_profile_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_profile_app_role
    ADD CONSTRAINT fk_user_profile_app_role_user_profile_id FOREIGN KEY (user_profile_id) REFERENCES public.user_profile(id);


--
-- Name: user_profile fk_user_profile_firm_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_profile
    ADD CONSTRAINT fk_user_profile_firm_id FOREIGN KEY (firm_id) REFERENCES public.firm(id);


--
-- Name: user_profile_office fk_user_profile_office_office_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_profile_office
    ADD CONSTRAINT fk_user_profile_office_office_id FOREIGN KEY (office_id) REFERENCES public.office(id);


--
-- Name: user_profile_office fk_user_profile_office_user_profile_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_profile_office
    ADD CONSTRAINT fk_user_profile_office_user_profile_id FOREIGN KEY (user_profile_id) REFERENCES public.user_profile(id);


--
-- Name: user_profile fk_user_profile_user_id; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.user_profile
    ADD CONSTRAINT fk_user_profile_user_id FOREIGN KEY (entra_user_id) REFERENCES public.entra_user(id);


--
-- Name: role_assignment fkinkufwdifl4xw55uexx0avmid; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT fkinkufwdifl4xw55uexx0avmid FOREIGN KEY (assignable_role_id) REFERENCES public.app_role(id);


--
-- Name: role_assignment fksq2gkf49sdafy34jxa1rh1ya6; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT fksq2gkf49sdafy34jxa1rh1ya6 FOREIGN KEY (assigning_role_id) REFERENCES public.app_role(id);


--
-- Name: spring_session_attributes spring_session_attributes_fk; Type: FK CONSTRAINT; Schema: public; Owner: cptJXFyh0U
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES public.spring_session(primary_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict oLUbbXjtDeD9xw74pUsSWOBZzQ9QcAcDc0ETxnZlqYPzCTBeAfky0oXa3AFf86V

