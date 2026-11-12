# LAA Data User API Copilot instructions

Apply to code generation and pull request reviews. Use British English and MOJ for Ministry of Justice.

## Architecture and SOLID

- Code MUST follow SOLID. Review responsibilities, coupling, testability and complexity. Challenge oversized classes; size alone does not determine responsibility.
- **SRP:** Give each class one reason to change. Separate HTTP handling, business rules, validation, mapping, persistence, authorisation, caching, configuration and integrations. Prefer focused handlers, repositories and mappers.
- **OCP:** Prefer composition and extension points over growing conditionals or modifying established flows. Use feature flags to select alternatives where appropriate.
- **LSP:** Honour interface contracts without unexpected unsupported operations or implementation-specific handling in consumers.
- **ISP:** Keep interfaces cohesive; clients must not depend on unused methods. Preserve CQRS-lite read/write separation.
- **DIP:** Business logic depends on abstractions, not concrete infrastructure. Use constructor injection and Spring configuration to wire implementations; avoid field injection and constructing infrastructure in services.
- Keep entry points thin, including controllers, command/query handlers, message consumers and function handlers. Adapt input, apply boundary concerns, delegate to application services and map results; do not embed business rules or infrastructure operations.
- Application services own use-case orchestration and coordinate domain rules, persistence, integrations and result assembly through focused collaborators. Do not turn services into god classes.
- A growing constructor dependency list signals potentially mixed responsibilities and must trigger an architectural review. Split along cohesive responsibilities rather than imposing an arbitrary dependency limit or hiding dependencies behind a facade.
- Keep HTTP request, response and session objects out of services; session management belongs in controllers.
- Preserve CQRS-lite boundaries in README.md and existing ArchUnit tests. Query results must not leak persistence entities.

## Domain modelling

- Represent domain outcomes and business meaning with types, not free-text strings. Prefer enums such as `PolicyDenialReason`, domain-specific exceptions and strongly typed identifiers where identifiers have distinct meanings.
- Do not use string-based business outcomes or string matching to control behaviour. Do not encode business rules in log or exception messages; messages describe typed outcomes, not define them.
- Translate typed outcomes into client-facing text at the response boundary.

## Transactions and external systems

- Define explicit, short database transaction boundaries around cohesive persistence operations. Do not keep a database transaction or lock open while calling an external system; a slow or hanging call must not hold database resources.
- Review the full call chain: a remote call remains inside a transaction inherited from a caller even if the integration method itself has no `@Transactional` annotation.
- Database rollback cannot undo a successful external operation. Define failure recovery and retry/idempotency behaviour explicitly when coordinating local and remote changes.
- Protect concurrent state transitions without holding database locks across remote calls. Use an explicit coordination approach, such as a persisted pending state with version checks or an outbox workflow, suited to the consistency requirements; do not simply release a lock and rely on a stale pre-check.

## Configuration and testability

- Separate configuration concerns. Bind external values in configuration classes or typed properties; supply dependencies through constructors, not raw configuration injection in services.
- Avoid static state, HTTP sessions and direct system-time access in business logic; inject a clock where needed. Test business behaviour without a Spring context wherever practical.
- Add automated tests for new behaviour and fixes. Test behaviour, not implementation details. Use existing Gradle and architecture checks.

## Errors and logging

- Use descriptive, specific exception types; catch the narrowest applicable type. Broad handling belongs only at defined application boundaries.
- Use domain-specific exception types such as `UserNotFoundException`, `UserProfileNotFoundException`, and `FirmNotFoundException`, with fixed identifier-based message templates. Don't use generic `RuntimeException`s or ad hoc messages for business outcomes.
- Keep try blocks limited to operations needing handling; put setup, mapping and follow-up outside. Describe precisely what failed. Handling must recover, retry, translate, enrich or propagate, never swallow failures or hide root causes.
- Use the existing Logstash Logback encoder and structured key-value fields. Keep messages stable with an event name and relevant context; do not concatenate or embed variable values in messages.
- Pass exceptions as the final logging argument so the stack trace remains in one JSON event. Logs must be searchable and filterable. Never expose exception details in UI/API errors or log secrets.
- Decide logging at the boundary responsible for handling the failure; avoid logging and rethrowing the same failure at multiple layers. Log expected business failures at WARN and unexpected faults at ERROR.

## Data and files

- Avoid N+1 queries and repository calls inside loops when batch access is possible. Prefer methods such as `findByIdIn(...)`; assess scalability against expected data volumes.
- Do not use `Object[]`, positional indexes or query column-order assumptions in business logic. Map results to records, named fields or typed projections.
- Prefer in-memory streaming to local files. When unavoidable, use `Files.createTempFile(...)` and remove files on success and failure. Do not rely on local filesystem persistence unless explicitly required.
- Centralise CSV generation in a shared, injectable component. Reuse configured `CsvMapper` instances and schema definitions rather than recreating them for each export.
- Keep export implementations focused on mapping business data; delegate CSV construction and serialization to the shared component. Design it for reuse by CSV export schedulers moving into this API.

## Maintainability

- Reuse functionality; avoid duplicate business rules, magic values, untyped data and unnecessary dependencies. Use meaningful names and domain types.
- Existing code is not authority. Address relevant violations without unrelated refactoring. Passing tests and green CI do not replace architectural review.
- Treat AI output as a draft. Reject changes that increase coupling, hide dependencies, mix responsibilities or add unnecessary complexity.

## Endpoint implementation defaults

The general engineering guardrails above apply across all features, not only CQRS-lite or RBAC. For every request to add or update an endpoint, also apply the specific CQRS-lite, RBAC, concurrency, exception, response, and testing requirements below by default. Treat them as mandatory unless I explicitly say otherwise. If authorization scope, response behaviour, transaction semantics, or existing behaviour is ambiguous, ask before implementing.

### Scope

- Change only what the task needs. Don't refactor unrelated code or fix unrelated pre-existing issues.
- Keep behaviour changes separate from mechanical extraction and call out any intentional behaviour change.

### CQRS-lite boundaries

- Command and query sides must not depend on each other's handlers, services, repositories, mappers, or policies.
- Handlers must never call other handlers. Share same-side logic through a neutral service, not a handler or another handler's command.
- Handlers act as authorization entry points and delegate to same-side application services.
- Use `@Transactional(readOnly = true)` for database query operations; query services never mutate external systems or return entities. Keep any remote reads outside database transactions.
- Define command persistence transactions explicitly; do not annotate an entire handler or service flow when it includes remote calls.
- Don't put persistence entities in handler, service, or policy contracts. Use DTOs, scalars, or context records.

### RBAC

- Declare authorization on the command/message with `@RequiresPermission` or `@PolicyGuarded`. Don't put ad hoc permission checks in handlers or services.
- RBAC applies only to `handle` methods on `AuthorizationEntryPoint` implementations. Shared services must not be authorization entry points.
- Policies are pure: they evaluate trusted facts and don't access repositories or services directly. Load facts through an `AuthorizationPolicyInputResolver`, with one lookup per decision.
- Authorization fails closed: null or unsupported inputs deny. Misconfiguration raises `InvalidAuthorizationConfigurationException`.
- Policies return non-null `PolicyDecision`s with a `PolicyDenialReason` enum. Denial reasons go to structured logs, never to clients.
- Map only roles marked `authzRole` to `AuthzRole` enums. Never compare role-name strings directly.
- Bulk operations authorize once at the bulk command and firm level. Don't reapply per-user command authorization unless I ask.

### Concurrency and lifecycle

- If a pre-handler check can race with execution, recheck the condition within the protected state transition. Use locking or version checks for local database changes and explicit coordination for workflows involving remote calls, following the general transaction guardrails.
- Never let lower-privilege actions downgrade higher-privilege state.
- Keep legacy null deactivation types distinct from `NONE`.
- Bulk work: isolate each item's persistence transactions, keep remote calls outside them, continue past individual failures, and classify outcomes with types (invalid data, not found, denied, downstream, rejected, unexpected).

### Exceptions and responses

- Missing and inaccessible users/profiles return the same generic 403. Firms and other missing resources return 404.
- Client responses must not expose identifiers, roles, firm details, configuration diagnostics, or stack details. Keep diagnostics in server logs.
- Share repeated client-response strings through constants such as `ForbiddenResponseConstants`.

### Tests and validation

- Add focused tests for new behaviour and every authorization or concurrency rule.
- Prove authorization through real Spring-proxied handlers. Test fixtures must isolate the rule under test so the test fails if that rule is removed.
- Use database-backed tests for locking and concurrency fixes.
- Add or update ArchUnit rules when introducing a new boundary.
- Update the README for any behaviour or architecture change.
- Validate with:
  `./gradlew test --no-daemon --console=plain --tests '*Pattern*' checkstyleMain checkstyleTest`
  (`--tests` must come directly after `test`.) Run the full suite when Spring wiring or shared behaviour changes.

### Delivery

- Don't commit, push, or post PR comments unless I ask.
- Finish with a short summary of the changes, any intentional behaviour changes, and any open decisions.
