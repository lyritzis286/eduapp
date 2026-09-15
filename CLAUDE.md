# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

EduApp is a Spring Boot 4 REST API (Java 21, Amazon Corretto toolchain) for managing a teacher registry for Coding Factory @ AUEB. Package root: `gr.aueb.cf.eduapp`. Persistence is MySQL via Spring Data JPA + Flyway migrations; auth is stateless JWT with capability-based authorization.

## Build, run, test

Windows: use `gradlew.bat`; POSIX shells: `./gradlew`.

- Build: `./gradlew build`
- Run the app: `./gradlew bootRun`
- Run all tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests "gr.aueb.cf.eduapp.EduAppApplicationTests"`
- Run a single test method: `./gradlew test --tests "gr.aueb.cf.eduapp.EduAppApplicationTests.contextLoads"`

Currently the only test present is the default context-load smoke test (`EduAppApplicationTests`) — there is no meaningful automated coverage of controllers/services yet.

### Local configuration

The app reads a `.env` file at the repo root (via `spring.config.import=optional:file:.env`) for secrets/config not committed to git. Required variables (see `.env.example`): `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER`, `MYSQL_PASSWORD`, `JWT_SECRET_KEY` (base64-encoded, used as an HS256 signing key).

Active Spring profile is `dev` (`application.properties` sets `spring.profiles.active=dev`); profile-specific settings live in `application-dev.properties`. `spring.jpa.hibernate.ddl-auto=validate` — schema changes must go through Flyway migrations, not Hibernate auto-DDL.

Uploaded files are written to `file.upload.dir` (`uploads/`), logs to `logs/eduapp.log`. Postman collections for manual API testing live under `postman/`.

## Architecture

Standard layered flow: `api` (REST controllers) → `service` (interface + impl) → `repository` (Spring Data JPA) → `model` (JPA entities), with `dto` and `mapper` bridging entities and the wire format.

- **DTOs are Java records**, split per operation: `*InsertDTO`, `*UpdateDTO`, `*ReadOnlyDTO` (e.g. `TeacherInsertDTO`, `TeacherUpdateDTO`, `TeacherReadOnlyDTO`). Nested DTOs compose (e.g. `TeacherInsertDTO` embeds `UserInsertDTO` and `PersonalInfoInsertDTO`).
- **`Mapper`** (`mapper/Mapper.java`) is a single hand-written component doing all entity↔DTO conversion — there is no MapStruct/ModelMapper. Add new mapping methods here rather than scattering conversion logic in services.
- **Services expose an interface** (e.g. `ITeacherService`) implemented by a concrete class (`TeacherService`); controllers depend on the interface.
- **Filtering/search** uses a `*Filters` object (`core/filters/TeacherFilters`) bound from query params via `@ModelAttribute`, combined with a JPA `Specification` (`specification/TeacherSpecification`) for dynamic queries, with direct-lookup shortcuts (by uuid/vat/amka) bypassing the Specification when a single unique field is provided.

### Entities and soft delete

All entities extend `AbstractEntity` (`model/AbstractEntity.java`), a `@MappedSuperclass` providing `createdAt`/`updatedAt` (via `AuditingEntityListener`) and soft-delete fields (`deleted`, `deletedAt`) with a `softDelete()` helper. **Soft delete is not enforced globally** — there's no `@Where` clause or filter, so repository/service methods must explicitly query `...DeletedFalse` variants where "active" records are intended (see `TeacherRepository.findByUuidAndDeletedFalse` vs. plain `findByUuid`). When adding new lookups, check whether deleted records should be excluded.

Entities use `UUID` (stored as `BINARY(16)`) as the externally-exposed identifier and a separate auto-increment `Long id` as the internal PK. Bidirectional associations use paired `add*`/`remove*` methods on the owning side to keep both sides in sync (e.g. `Teacher.addUser`, `Role.addUser`, `Region.addTeacher`), rather than raw setters — prefer these helpers over calling setters directly on both sides.

### Authorization model

Authorization is **capability-based**, not just role-based. `roles` have many `capabilities` (`roles_capabilities` join table, seeded in `db/migration/V3__insert_roles_capabilites.sql`); `User.getAuthorities()` grants both `ROLE_<name>` and one `SimpleGrantedAuthority` per capability (e.g. `VIEW_TEACHER`, `EDIT_TEACHER`, `VIEW_ONLY_TEACHER`). Method security is enabled (`@EnableMethodSecurity`) and most service methods (not controllers) are annotated `@PreAuthorize("hasAuthority('...')")`. Coarse URL-level rules also exist in `SecurityConfiguration.securityFilterChain` — when changing access rules, both layers may need updating.

For "own resource" access, `SecurityService` (`security/SecurityService.java`, bean name `securityService`) exposes predicate methods like `isOwnTeacherProfile(uuid, authentication)` referenced from SpEL in `@PreAuthorize` expressions (e.g. `hasAuthority('VIEW_ONLY_TEACHER') and @securityService.isOwnTeacherProfile(#uuid, authentication)`).

Auth flow: `AuthRestController` → `AuthenticationService` issues a JWT via `JwtService` (jjwt, HS256); `JwtAuthenticationFilter` validates the bearer token on each request and loads the user through `CustomUserDetailsService`. Sessions are stateless (`SessionCreationPolicy.STATELESS`); no CSRF (disabled — API-only, token-based).

### Error handling

All exceptions extend `AppGenericException` (`core/exceptions/`), carrying a `code` string plus message. `ErrorHandler` (`core/ErrorHandler.java`, a `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`) centrally maps each exception type to an HTTP status and an `ErrorResponseDTO`/`ValidationErrorResponseDTO`. When adding a new failure case, prefer adding/reusing an `AppGenericException` subclass and a handler here over throwing raw exceptions from controllers/services.

Bean validation failures are collected manually: controllers run a validator (e.g. `TeacherInsertValidator`) against `BindingResult`, then throw `ValidationException` wrapping the `BindingResult` if errors exist — `ErrorHandler` turns that into a field-name→message map.

### File uploads

`TeacherService.saveAmkaFile` shows the pattern for attachment uploads: detect content type with Apache Tika, build an `Attachment` entity, and defer the actual filesystem write (and deletion of the old file) to `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()` — so a DB rollback never leaves an orphaned file, and (per the logged warning) a post-commit filesystem failure is treated as a recoverable/logged condition rather than a rolled-back transaction. Reuse this pattern for other file-upload endpoints. This method is also annotated `@Retryable` (Spring's resilience support) for transient IO/HTTP failures.

### API documentation

springdoc-openapi is wired up; Swagger UI is served at `/swagger-ui.html` / `/swagger-ui/**` (permitted without auth). `OpenApiConfig` registers the `Bearer Authentication` security scheme and an `OperationCustomizer` that auto-adds 401/403 responses to any operation whose method or controller carries `@SecurityRequirement` — so secured endpoints don't need to redeclare those responses manually in `@ApiResponses`.

### Database migrations

Flyway migrations live in `src/main/resources/db/migration/`, named `V<n>__description.sql`. Note some existing filenames contain unexpected spaces (e.g. `V1__initial_schema 2.sql`) — this is pre-existing, not a typo to silently "fix", since renaming an applied migration breaks Flyway's checksum tracking.
