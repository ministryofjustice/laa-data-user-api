# silas-contracts

Shared request/response DTOs and enums for the SiLAS service boundary.

Published to [GitHub Packages](https://github.com/orgs/ministryofjustice/packages?repo_name=laa-data-user-api) as:

```
uk.gov.justice.laa:silas-contracts:<version>
```

---

## Consuming the library

Add the GitHub Packages registry and dependency to your `build.gradle`:

```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/ministryofjustice/laa-data-user-api")
        credentials {
            username = System.getenv("GITHUB_ACTOR")?.trim() ?: providers.gradleProperty("gitPackageUser").orNull
            password = System.getenv("GITHUB_TOKEN")?.trim() ?: providers.gradleProperty("gitPackageKey").orNull
        }
    }
}

dependencies {
    implementation 'uk.gov.justice.laa:silas-contracts:0.1.0'
}
```

Both `laa-data-user-api` (the server) and `laa-landing-page` (the OBO client) consume this library.
`laa-data-user-api` references it via a local Gradle project dependency during development:

```groovy
// laa-data-user-api/build.gradle — use the local submodule, not the published artifact
implementation project(':silas-contracts')
```

---

## Package layout

| Package | Contents |
|---|---|
| `uk.gov.justice.laa.silas.contracts.request` | `DisableUserRequest`, `EnableUserRequest` |
| `uk.gov.justice.laa.silas.contracts.response` | `UserProfileDetailResponse`, `AppRoleResponse`, `OfficeResponse` |
| `uk.gov.justice.laa.silas.contracts.enums` | `UserType`, `UserAccountStatus`, `UserProfileStatus` |

### Key design constraints

- **No Spring dependencies** — the JAR must be consumable without a Spring Boot classpath.
- **No secrets** — no hardcoded credentials, tokens, or environment-specific values.
- `jakarta.validation-api` annotations (`@NotNull`, `@NotBlank`) are included to express field-level contract intent; runtime validation enforcement is the consumer's responsibility.
- Lombok is a `compileOnly` dependency — not transitive.

---

## Publishing a new version

1. Merge your changes to `main`.
2. Push a tag in the form `silas-contracts-vX.Y.Z`:
   ```bash
   git tag silas-contracts-v0.2.0
   git push origin silas-contracts-v0.2.0
   ```
3. The `publish-contracts` CI workflow publishes the JAR to GitHub Packages automatically.

---

## Versioning policy (Semantic Versioning)

| Change | Version bump | Safe to deploy independently? |
|---|---|---|
| Add optional field to a response DTO | **Minor** (0.1 → 0.2) | Yes — Jackson ignores unknown fields by default |
| Add a new DTO class | **Minor** | Yes |
| Add a new enum value | **Minor** | Yes, if consumers handle unknown values gracefully |
| Remove or rename any field or class | **Major** (0.x → 1.0, 1.x → 2.0) | **No** — coordinate both repos |
| Change a field type | **Major** | **No** — coordinate both repos |
| Remove an enum value | **Major** | **No** |

### During PoC phase (0.x)

While the version is `0.x`, minor version bumps may include breaking changes at the team's discretion.
Once both repos are in production use, bump to `1.0.0` and apply strict semver.

### Duplicate vs upgrade

If a breaking change is needed and coordinating both repos simultaneously isn't practical:
1. Add the new DTO under a new class name (e.g., `DisableUserRequestV2`).
2. Deprecate the old class with `@Deprecated`.
3. Remove the old class in the next major version once both consumers are updated.

---

## Local development

When working on `laa-data-user-api`, the submodule is available as a project dependency — no publish step needed:

```bash
./gradlew :silas-contracts:build
```

To test the published artifact locally before tagging, use `publishToMavenLocal`:

```bash
./gradlew :silas-contracts:publishToMavenLocal -PcontractsVersion=0.1.0-LOCAL
```

Then add `mavenLocal()` to the consumer's repositories temporarily.
