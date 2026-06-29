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

| Variable Name | Description | Default Value |
| --- | --- | --- |
| `POSTGRES_DB_ADDRESS` | The host address for the Postgres database. | `localhost` |
| `POSTGRES_DB_NAME` | The name of the Postgres database. |
| `POSTGRES_USERNAME` | The username to authenticate with Postgres. | `postgres` |
| `POSTGRES_PASSWORD` | The password to authenticate with Postgres. | `postgres` |

</details>

---

## TODO 

> This section is a work in progress, it currently only contains immediate baseline goals

- [ ] Integrate Database 
- [ ] Add OpenAPI/Swagger documentation generation.
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
