# LAA Data User API

Fill me in.

---

## Prerequisites

- Java 21
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

- [ ] Integrate Database 
- [x] Add OpenAPI/Swagger documentation generation.
- [ ] Add IRSA & fix deployment serviceaccount name
- [ ] API Authentication
- [ ] Dependabot configuration
- [ ] Sentry configuration
- [x] Checkstyle configured
- [ ] Grafana / Prometheus configuration
- [ ] AlertManager configuration
- [ ] CodeQL configuration
- [ ] Linter check configuration
- [ ] ASH scan configuration
- [ ] Branching strategy setup & promotion pipeline configured
- [ ] GitGuardian configuration
- [ ] GitLeaks configuration
- [ ] Trufflehog configuration
- [ ] Snyk configuration
- [ ] Test coverage on PRs setup
- [ ] ZAP scan setup
- [ ] Pingdom setup
