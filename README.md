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
 
Deploying a specific, already-tested build to `test` or `prod` is a separate, manual two-step process — the same image built for dev is promoted, nothing is re
built:
 
1. **Cut a release** — tag a commit already on `main`:
   ```bash
   git tag v1.2.0 <commit-sha>
   git push origin v1.2.0
   ```
   Pushing a `v*.*.*` tag triggers `.github/workflows/release.yml`, which re-tags that commit's existing ECR image (`:<sha>`) as `:v1.2.0` — it fails if that c
ommit was never deployed to dev, since the source image wouldn't exist yet.
 
2. **Deploy the release** — manually run `.github/workflows/deploy_release.yml` from the Actions tab (`Deploy Release` → `Run workflow`), choosing:
   - `environment`: `test` or `prd`
   - `version`: the tag from step 1, e.g. `v1.2.0`
 
   This runs `helm upgrade --install` against that environment's Cloud Platform namespace, using the chart at `deployment/helm/laa-data-user-api` with `values.
yaml` layered under `values-test.yaml`/`values-prd.yaml`. `prd` runs are gated by whatever GitHub Environment protection rules (e.g. required reviewers) are
configured on the `prd` environment.
 
> **Note:** the `prd` Cloud Platform namespaces and their GitHub Environment secrets aren't provisioned yet — see the TODO list below.
 
---

## Environment Variables

The application relies on several environment variables for configuration. Below is a list of the variables currently configured in the project:

<details>
<summary>Click to expand</summary>

| Variable Name                       | Description                                           | Default Value |
|-------------------------------------|-------------------------------------------------------|---------------|
| `POSTGRES_DB_ADDRESS`               | The host address for the Postgres database.           | `localhost`   |
| `POSTGRES_DB_NAME`                  | The name of the Postgres database.                    |
| `POSTGRES_USERNAME`                 | The username to authenticate with Postgres.           | `postgres`    |
| `POSTGRES_PASSWORD`                 | The password to authenticate with Postgres.           | `postgres`    |
| `AZURE_TENANT_ID`                   | The Azure AD tenant ID.                               | None          |
| `TECH_SERVICES_AZURE_SCOPE`         | The Azure AD scope for the Tech Services API.         | None          |
| `TECH_SERVICES_AZURE_CLIENT_ID`     | The Azure AD client ID for the Tech Services API.     | None          |
| `TECH_SERVICES_AZURE_CLIENT_SECRET` | The Azure AD client secret for the Tech Services API. | None          |
| `TECH_SERVICES_TENANT_ID`           | The Azure AD tenant ID for the Tech Services API.     | None          |
| `TECH_SERVICES_BASE_URL`            | The base URL for the Tech Services API.               | None          |
| `TECH_SERVICES_CALLS_ENABLED`       | Whether to enable calls to the Tech Services API.     | `false`       |
| `TECH_SERVICES_LAA_BUSINESS_UNIT`   | The business unit for the Tech Services API.          | `laa`         |
| `TECH_SERVICES_REQ_READ_TIMEOUT`    | The read timeout for the Tech Services API.           | `30`          |
| `TECH_SERVICES_REQ_CONNECT_TIMEOUT` | The connect timeout for the Tech Services API.        | `30`          |

</details>

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
- [ ] AlertManager configuration
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
- [ ] Pingdom setup
