# LAA Data User API

Fill me in.

---

## Prerequisites

- Java 25
- Docker (optional, for running dependencies or the application container)

### GitHub Packages Authentication
This project relies on the LAA GitHub Package Registry (`maven.pkg.github.com/ministryofjustice/laa-spring-boot-common`). To successfully download dependencies and build the project, you **must** configure your environment with a GitHub Personal Access Token (PAT).

1. Generate a classic PAT with `read:packages` permissions.
2. Export the credentials in your local environment (`~/.zshrc` or `~/.bashrc`):
```bash
export GITHUB_ACTOR="<your-github-username>"
export GITHUB_TOKEN="<your-personal-access-token>"
```

---

## Running Locally

To start the application locally using Gradle, run:

```bash
./gradlew bootRun
```

The application will start on port `8080` by default. You can test the application is running by visiting the health endpoint:
[http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## Testing 

To run the full suite of unit and integration tests:

```bash
./gradlew test
```

---

## Docker

To build the Docker image locally:
```bash
docker build -t laa-data-user-api .
```

---

## Releases

Pushes to `main` deploy automatically to `development` (`.github/workflows/deploy_dev.yml`), building a fresh image tagged with the commit SHA.

Deploying a specific, already-tested build to `test` or `prod` is a separate, manual two-step process — the same image built for dev is promoted, nothing is rebuilt:

1. **Cut a release** — tag a commit already on `main`:
   ```bash
   git tag v1.2.0 <commit-sha>
   git push origin v1.2.0
   ```
   Pushing a `v*.*.*` tag triggers `.github/workflows/release.yml`, which re-tags that commit's existing ECR image (`:<sha>`) as `:v1.2.0` — it fails if that commit was never deployed to dev, since the source image wouldn't exist yet.

2. **Deploy the release** — manually run `.github/workflows/deploy_release.yml` from the Actions tab (`Deploy Release` → `Run workflow`), choosing:
   - `environment`: `test` or `prd`
   - `version`: the tag from step 1, e.g. `v1.2.0`

   This runs `helm upgrade --install` against that environment's Cloud Platform namespace, using the chart at `deployment/helm/laa-data-user-api` with `values.yaml` layered under `values-test.yaml`/`values-prd.yaml`. `prd` runs are gated by whatever GitHub Environment protection rules (e.g. required reviewers) are configured on the `prd` environment.

> **Note:** the `prd` Cloud Platform namespaces and their GitHub Environment secrets aren't provisioned yet — see the TODO list below.

---

## Environment Variables

The application relies on several environment variables for configuration. Below is a list of the variables currently configured in the project:

<details>
<summary>Click to expand</summary>

| Variable Name                       | Description                                           | Default Value | Source |
|-------------------------------------|-------------------------------------------------------|---------------|--------|
| `POSTGRES_DB_ADDRESS`               | The host address for the Postgres database.           | `localhost`   | K8s Secret `rds-postgresql-instance-output` |
| `POSTGRES_DB_NAME`                  | The name of the Postgres database.                    |               | K8s Secret `rds-postgresql-instance-output` |
| `POSTGRES_USERNAME`                 | The username to authenticate with Postgres.           | `postgres`    | K8s Secret `rds-postgresql-instance-output` |
| `POSTGRES_PASSWORD`                 | The password to authenticate with Postgres.           | `postgres`    | K8s Secret `rds-postgresql-instance-output` |
| `AZURE_TENANT_ID`                   | The Azure AD tenant ID, used to validate inbound JWTs. | None          | K8s Secret `laa-data-user-api-azure-tenant-secret-k8s` |
| `AZURE_CLIENT_ID`                   | The Azure AD client (app) ID, used to validate inbound JWTs' audience. | None | K8s Secret `laa-data-user-api-azure-client-id-k8s` |
| `TECH_SERVICES_AZURE_SCOPE`         | The Azure AD scope for the Tech Services API.         | None          | Helm value (GitHub Actions var) |
| `TECH_SERVICES_AZURE_CLIENT_ID`     | The Azure AD client ID for the Tech Services API.     | None          | GitHub Actions secret |
| `TECH_SERVICES_AZURE_CLIENT_SECRET` | The Azure AD client secret for the Tech Services API. | None          | GitHub Actions secret |
| `TECH_SERVICES_TENANT_ID`           | The Azure AD tenant ID for the Tech Services API.     | None          | GitHub Actions secret |
| `TECH_SERVICES_BASE_URL`            | The base URL for the Tech Services API.               | None          | GitHub Actions secret |
| `SLACK_WEBHOOK_URL`                 | Slack Incoming Webhook URL the `laa-data-user-api-uptime-check` CronJob posts to on a failed external health check. Not consumed by the main app. | None | K8s Secret `slack-alert-webhook-secret` |
| `TECH_SERVICES_CALLS_ENABLED`       | Whether to enable calls to the Tech Services API.     | `false`       | Not yet wired into the Helm chart (`application.yml` default only) |
| `TECH_SERVICES_LAA_BUSINESS_UNIT`   | The business unit for the Tech Services API.          | `laa`         | Not yet wired into the Helm chart (`application.yml` default only) |
| `TECH_SERVICES_REQ_READ_TIMEOUT`    | The read timeout for the Tech Services API.           | `30`          | Not yet wired into the Helm chart (`application.yml` default only) |
| `TECH_SERVICES_REQ_CONNECT_TIMEOUT` | The connect timeout for the Tech Services API.        | `30`          | Not yet wired into the Helm chart (`application.yml` default only) |

Note there are two distinct Azure AD app registrations in play here, each with its own credentials: the values under `AZURE_*` validate JWTs presented *to* this API (inbound), while `TECH_SERVICES_AZURE_*`/`TECH_SERVICES_TENANT_ID` authenticate this API's outbound calls *to* the Tech Services API. There's currently no `AZURE_CLIENT_SECRET` — the inbound-validation app registration only needs the tenant and client ID.

> **Orphaned secret:** a Kubernetes Secret named `laa-data-user-api-azure-client-secret-k8s` also exists in the namespace,  However, nothing in this repo — chart or code — consumes it: there's no `secretKeyRef` for it in `templates/deployment.yaml`, and no `AZURE_CLIENT_SECRET` env var or `@Value` binding anywhere in the codebase (the only client secret actually read by the code is `TECH_SERVICES_AZURE_CLIENT_SECRET`, via `TechServicesConfig`). 
</details>

### Adding a New Environment Variable

Deployment config flows: GitHub Actions secret/variable → `.github/workflows/reusable-helm-deploy.yml` → `deployment/helm/laa-data-user-api/values.yaml` → the chart's `templates/deployment.yaml` → the container env var → (optionally) `application.yml`. Which files you need to touch depends on whether the value is sensitive/deploy-time-only, or just ordinary static config.

**Case A — it's a secret, or its value is only known at deploy time** (e.g. an API key, or something CI computes like the image tag):

1. Add the GitHub Actions secret (or `vars.*` variable) in the relevant Environment(s) — Settings → Environments → `development`/`test`/`prd`.
2. In `.github/workflows/reusable-helm-deploy.yml`, in the "Generate values-secrets.yaml" step, add one line to the `yq eval` pipeline (e.g. `| .newThing = strenv(NEW_THING)`) and one line to that step's `env:` block (e.g. `NEW_THING: ${{ secrets.NEW_THING }}`).
3. Add the default in `deployment/helm/laa-data-user-api/values.yaml`.
4. Add the container env entry in `deployment/helm/laa-data-user-api/templates/deployment.yaml`.
5. Reference it in `src/main/resources/application.yml` if Spring needs to read it.

**Case B — it's an ordinary, non-sensitive value whose value is already known** (not computed by CI):

1. Add it directly to `deployment/helm/laa-data-user-api/values.yaml` (default) and/or `values-dev.yaml`/`values-test.yaml`/`values-prd.yaml` (per-environment override) — the same pattern already used for `ingress.className`/`allowlist.groups`.
2. Add the container env entry in `templates/deployment.yaml`.
3. Reference it in `application.yml` if needed.

Prefer Case B whenever possible — it needs no GitHub secret and no workflow changes, and the value stays visible and diffable in the PR that introduces it rather than living in GitHub Settings. Reach for Case A only when the value is genuinely sensitive or can't be known until deploy time.

**Case C — the value already exists as a Kubernetes Secret in the namespace, provisioned outside this repo:**

Some values never pass through Helm or GitHub Actions at all — they're read straight from a Kubernetes `Secret` object that already exists in the Cloud Platform namespace, via `secretKeyRef` in `templates/deployment.yaml`. For example:

```yaml
- name: AZURE_TENANT_ID
  valueFrom:
    secretKeyRef:
      name: laa-data-user-api-azure-tenant-secret-k8s
      key: AZURE_TENANT_ID
```

These Secrets are created by Terraform in the [`cloud-platform-environments`](https://github.com/ministryofjustice/cloud-platform-environments) repo. The Secret's *value* is populated separately — for the RDS credentials (`rds-postgresql-instance-output`) this happens automatically when the RDS instance is provisioned; for the Azure app-registration values (`laa-data-user-api-azure-tenant-secret-k8s`, `laa-data-user-api-azure-client-id-k8s`) the value is set/updated manually via the AWS console (Secrets Manager), and the External Secrets Operator (ESO) in the Kubernetes cluster manages the synchronisation to the Kubernetes Secret, which Kubernetes then mounts into the pod as an env var on the next rollout.

Use this pattern only for values that are already being provisioned this way at the infrastructure level — it's not something you can opt into for an arbitrary new variable from within this repo alone; it requires a corresponding change in `cloud-platform-environments` first. To add one:

1. Add/confirm the Secret and its key exist in the namespace (via `cloud-platform-environments`, or check with `kubectl get secret <name> -n <namespace> -o yaml`).
2. Add the `secretKeyRef` env entry in `deployment/helm/laa-data-user-api/templates/deployment.yaml`, pointing at that Secret's name/key.
3. Reference it in `application.yml` if Spring needs to read it.

Unlike Case A, there's no GitHub Actions secret and no `values-secrets.yaml` entry — the value never touches CI at all, and updating it in AWS doesn't require a deploy (the running pod picks it up on its next restart/rollout, once Cloud Platform's sync has run).

---

## TODO 

> This section is a work in progress, it currently only contains immediate baseline goals

- [x] Integrate Database 
- [x] Add OpenAPI/Swagger documentation generation.
- [ ] Add IRSA
- [x] Fix deployment serviceaccount name
- [ ] API Authentication
- [ ] Dependabot configuration
- [x] Sentry configuration
- [x] Checkstyle configured
- [x] Grafana / Prometheus configuration
- [x] AlertManager configuration
- [x] CodeQL configuration
- [x] Linter check configuration
- [x] ASH scan configuration
- [x] Branching strategy setup & promotion pipeline configured
- [x] GitGuardian configuration
- [x] GitLeaks configuration
- [x] Trufflehog configuration
- [x] Snyk configuration
- [ ] Test coverage on PRs setup
- [x] ZAP scan setup
- [x] Pingdom setup
